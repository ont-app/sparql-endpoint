(ns sparql-endpoint.core
  (:import 
   [org.apache.jena.datatypes.xsd XSDDatatype]
   [org.apache.jena.query QueryFactory]
   )
  (:require
   [clojure.string :as s]
   [clojure.data.json :as json]
   [taoensso.timbre :as log]
   [clj-http.client :as http]))


          
(defn parse-prologue
  "Returns [<base>, <uri-to-qname>, <qname-to-uri>] parsed from the 
    <prologue> to `query`
  Where
  <base> is a URI string for the base of <query>
  <uri-to-qname> := fn[<uri>] -> <quickname>, or <uri> if there was no matching
    prefix in <prolog>
  <qname-to-uri> := fn[<qname>] -> <uri> (angle-bracketed)  or <qname> if there
    was no matching prefix in <prologue>
  <uri> is typically a URI with a prefix defined in the <prologue>, but
    may be any string
  <qname> is typically a qname with a prefix defined in <prologue>,
    but may be any string
  <prologue> is the prologue parsed from <query>, for which see
    spex at <https://www.w3.org/TR/sparql11-query/>.
  "
  [query]
  {
   :pre [(string? query)]
   :post [(let [[base, u-to-q, q-to-u] %]
            (and (string? base)
                 (fn? u-to-q)
                 (fn? q-to-u)))]
   }
  (let [unquote (fn [s] (s/replace s #"[<>]" ""))
        angle-quote (fn [s] (if s (str "<" s ">")))
        q (. QueryFactory create query)
        p (.getPrologue q)
        base (angle-quote (.getBaseURI p))
        ]
    
    [base
     (fn[u] (let [qname (.shortForm p (unquote u))]
              (if (not= qname u)
                qname
                (angle-quote u))))
     (fn[u] (or (angle-quote (.expandPrefixedName p u)) u))]))
        

(defn sparql-update
  "Side Effect: Modifies the contents of `endpoint` per the update query 
  `update`, possibly informed by http parameters  `http-req'
  Where
  <endpoint> is a SPARQL update endpoint
  <update> is a SPARQL update expression
  <http-req> := {?param...}
  <param> is anything described in <https://github.com/dakrone/clj-http>
    Though :form-params and :accept  will be overridden.
    This may be used for authentication parameters for example.
  "
  ([endpoint update]
   (sparql-update endpoint update {}))
  
  ([endpoint update http-req]
   {
    :pre [(re-find #"http" endpoint)
          (string? update)
          (map? http-req)
          ]
    }
   (let [response (http/post endpoint (merge (merge {:cookie-policy :standard}
                                                    http-req)
                                             {:form-params {:update update}
                                              :accept "text/plain"
                                              }))

         ]

     (case (:status response)
       200 (:body response)
       204 (log/info (str "Code 204:" response))
       :default (throw (Error. (str "No handler for status " 
                                    (:status response))))))))


(defn sparql-query
  "
  Returns output of (`render-results` <response-body>) for SPARQL `query`
    posed to `endpoint`, possibly informed by `http-req`
  Where
  <response-body> is the body of the response to <query>, posed to <endpoint>
    via an HTTP GET call which may be informed by <param>s in <http-req>
  <render-results> := fn(<query response>) -> e.g. true/false 
  (for an ASK query) or a map for a SELECT query, default is `identity`
  <http-req> := {?param...}, default is {}
  <param> is anything described in <https://github.com/dakrone/clj-http>
    typically :debug or authentication parameters
    Any :form-params will be overridden.
  "
  ([endpoint query]
   (sparql-query endpoint query {} identity))
  
  ([endpoint query http-req render-results]
   {
    :pre [(re-find #"http" endpoint)
          (re-find #"(?i)ASK|SELECT|CONSTRUCT" query)
          (fn? render-results)
          ]
    }
   (let [default-http-req {:cookie-policy :standard}
         response (http/get endpoint 
                            (merge (merge default-http-req
                                          http-req)
                                   {:query-params {:query query}
                                    }))
         ]
     (case (:status response)
       200  (render-results (:body response))
       400 (log/info (str "Code 400:" response))
       :default (throw (Error. (str "No handler for status " 
                                    (:status response))))))))


(def type-mapper
  "Maps datatype names to xsd datatypes"
  (. org.apache.jena.datatypes.TypeMapper getInstance))

(defn parse-xsd-value 
  "
  Returns <value> for `literal`
  Where
  <literal> := {?value ?datatype} is a literal value typically from a
    binding acquired from a select query.
  <value> is an instance of the xsd datatype specified for <literal>
  <datatype> is a string indicating the datatype associated with <value>,
    which may be an xsd datatype
  "
  [literal]
  (let [expand-xsd-prefix (fn[s]
                            (s/replace
                             s
                             #"xsd:"
                             "http://www.w3.org/2001/XMLSchema#"))
        type (.getTypeByName
              type-mapper
              (expand-xsd-prefix
               (get literal "datatype")))
        ]
    (if type
      (.parse type (get literal "value"))
      (get literal "value"))))


(def default-select-binding-handlers
  "A map with keys :uri :lang :datatype, each mapping to <binding-fn>
  Where
  <binding-fn> := (fn[binding]) -> value
  <binding> :={'value' <value>
               'type' <type>
               maybe 'xml:lang' <lang>
               maybe 'datatype' <datatype>}
  This is the default second arg to `standard-render-var-binding`
  "
  {:uri (fn[b] (str "<" (get b "value") ">"))
   :lang (fn[b] (get b "value"))
   :datatype parse-xsd-value
   })



(defn standard-render-var-binding
  "
  Returns {<var-keyword> <maybe-parsed-value>, ...} for each <var> in `binding`, with each [<var> <var-value>] pair interpreted by `binding-handlers`, which defaults to `default-select-binding-handlers`
  Where
  <binding> := {<var> {'type' <type>,
                       'value' <uri>|<literal>}
                ...}
    , typically returned by a SELECT query
  <var-keyword> is a keyword derived from <var>
  <maybe-parsed-value> is a translation of <value> appropriate for <type> and
    <datatype>, an instance of an appropriate class.
  <type> is one of #{'literal' 'uri'}
  <uri> is a valid URI string
  <literal> :=   {'
                  'value' <value>
                   maybe  'datatype' <datatype>
                   maybe  'xml:lang' <language-tag>
                   }, ...}}
  <value> is a string
  <datatype> may be and xsd value
  <language-tag> is e.g. 'en' for English.
  "
  ([binding]
   (standard-render-var-binding binding
                               default-select-binding-handlers))
   
  ([[var var-value] binding-handlers]
   (let [render-value (cond
                        (= (get var-value "type") "uri")
                        (:uri binding-handlers)
                        
                        (and (= (get var-value "type") "literal")
                             (contains? var-value "xml:lang"))
                        (:lang binding-handlers)
                        
                        (and (= (get var-value "type") "literal")
                             (contains? var-value "datatype"))
                        (:datatype binding-handlers)
                        :default
                        (fn[b] (get b "value")))
         
         ]
 
     (assert (fn? render-value))
     [(keyword var)
      (render-value var-value)])))

(defn prologue-informed-var-binding-fn[query]
  (let [[_ q-namer _] (parse-prologue query)]
    (fn[var-binding] (standard-render-var-binding
                      var-binding
                      (merge default-select-binding-handlers
                             {:uri (fn[b] (q-namer (get b "value"))) })))))

(defn sparql-select
  "
  Returns <bindings> for `query` posed to `endpoint`, handling
    literals according to `handle-literal`
  Where
  <query> := a SPARQL SELECT query
  <endpoint> the URL string of a SPARQL endpoint
  <handle-var-binding> := fn[<variable> <value>] -> <parsed-value>
    default is `maybe-parse-xsd`
  <bindings> := [<binding> , ...]
  <binding> := {<variable> <value>, ...}
  <variable> is a variable specified in <query>
  <value> := {?type ?value}
  <literal-value> := {'type' 'literal', ?datatype ?value}
  <parsed-value> is e.g. an int, float, etc. as is typically the case
    with standard xsd classes, or may be a custom value defined
    by the calling function.
  "
  ([endpoint query]
   (sparql-select endpoint query {} standard-render-var-binding))
  
  ([endpoint query http-req render-var-binding]
   {
    :pre [(re-find #"http"  endpoint)
          (string? query)
          (re-find #"(?i)SELECT" query)
          ;; (fn? render-var-binding)
          ]
    }
   (letfn [(render-select-results [http-response-body]
             ;; Returns [{<key> <value>, ...}, ...]
             (log/debug (str "http-response-body:"  http-response-body))
             (letfn [(render-binding [binding] ;; (fn [binding] 
                       (into {} 
                             (map render-var-binding
                                  binding)))
                     ]
               (vec
                (map render-binding
                     (-> http-response-body
                         (json/read-str)
                         (get "results")
                         (get "bindings"))))))]
           
     (log/debug query)
     (sparql-query endpoint
                   query
                   (merge http-req
                          {:accept "application/sparql-results+json"})
                   render-select-results))))


(defn sparql-ask
  "
  Returns boolean value per `query` posed to `endpoint`, through an
  HTTP call possibly informed by `http-req`.
  Where
  <query> is a SPARQL ASK query
  <endpoint> is a SPARQL endpoint
  <http-req> := {?param...}
  <param> is anything described in <https://github.com/dakrone/clj-http>
  Though #{:form-params :accept :saved-request?} will be overridden.

  "
  ([endpoint query]
   (sparql-ask endpoint query {}))
  
  ([endpoint query http-req]
   {
    :pre [(re-find #"http" endpoint)
          (re-find #"(?i)ASK*" query)
          ]
    :post [(contains? #{true false} %)]
    }
   (letfn [(render-ask-results [http-response-body]
             (log/debug (str "body:"  http-response-body))
             (-> http-response-body
                 (json/read-str)                 
                 (get "boolean")))
           ]
     (sparql-query endpoint
                   query
                   (merge http-req
                          {:accept "application/sparql-results+json"})
                   render-ask-results))))
  
(defn sparql-construct
  "
  Returns <expression> for `query` posed to `endpoint`, possibly informed by `http-req`
  Where
  <query> := a SPARQL CONSTRUCT query
  <endpoint> the URL string of a SPARQL endpoint
  <http-req> := {?param...}
  <param> is anything described in <https://github.com/dakrone/clj-http>
    Though :form-params and :save-request? will be overridden.
    The default :accept parameter is text/turtle.
  "
  ([endpoint query]
   (sparql-construct endpoint query {}))
  
  ([endpoint query http-req]
   {
    :pre [(re-find #"http"  endpoint)
          (string? query)
          (re-find #"(?i)CONSTRUCT" query)
          ]
    :post [(if (not (string? %)) (let [] (log/warn %) false) true)]
    }
   (sparql-query endpoint
                 query
                 (merge {:accept "text/turtle"}
                        http-req)
                 identity)))




(ns sparql-endpoint.core
  (:import 
   [org.apache.jena.datatypes.xsd XSDDatatype]
   [org.apache.jena.query QueryFactory]
   )
  (:require 
   ;;[clojure.tools.cli :as cli]
   [clojure.string :as str]
   [clojure.data.json :as json]
   [clojure.xml :as xml]
   [taoensso.timbre :as log]
   [clj-http.client :as http]

   ))


          
(defn parse-prologue
  "Returns [<base>, <uri-to-qname>, <qname-to-uri>] parsed from the 
    prologue to `query`
  Where
  <base> is a URI string for the base of <query>
  <uri-to-qname> := fn[<uri>] -> <quickname>
  <qname-to-uri> := fn[<qname>] -> <uri> (angle-bracketed)
  "
  [query]
  {
   :pre [(string? query)]
   :test (#(let [query "BASE : <http://example.org/>
                        PREFIX eg: <http://example.com/> 
                        Select * where {?s ?p ?o.}"]
             [base, u-to-q, q-to-u] = parse-prologue(query)
             (assert (= base "http://example.org/"))
             (assert (= (u-to-q "http://example.com/blah") "eg:blah"))
             (assert (= (q-to-u  "eg:blah") "http://example.com/blah"))
             (assert (= (q-to-u  "blah") "blah"))
             (assert (= (u-to-q  "blah") "blah"))))
             
   }
  (let [angle-quote (fn [s] (if s (str "<" s ">") ""))
        q (. QueryFactory create query)
        p (.getPrologue q)
        base (angle-quote (.getBaseURI p))
        ]
    [base
     (fn[u] (or (.shortForm p u) u))
     (fn[u] (or (angle-quote (.expandPrefixedName p u)) u))]))
        

(defn sparql-update
  "Side Effect: Modifies the contents of `endpoint` per the update query 
  `update`, possibly informed by `http-req'
  Where
  <endpoint> is a SPARQL update endpoint
  <update> is a SPARQL update expression
  <http-req> := {?param...}
  <param> is anything described in <https://github.com/dakrone/clj-http>
  Though :form-params :accept :saved-request? will be overridden.
  This can be used for e.g. authentication
  "
  ([endpoint update http-req]
   {
    :pre [(re-find #"http" endpoint)
          (string? update)
          (map? http-req)
          ]
    }
   (let [response (http/post endpoint (merge http-req
                                             {:form-params {:update update}
                                              :accept "text/plain"
                                              ;; :debug true
                                              :save-request? true
                                              }))
         content-type (:Content-Type (:headers response))

         ]

     (case (:status response)
       200 (:body response)
       204 (log/info (str "Code 204:" response))
       :default (throw (Error. (str "No handler for status " 
                                    (:status response)))))

     ))
  ([endpoint update]
   (sparql-update endpoint update {})))

(defn sparql-query
  "
  Returns output of (`render-results` <query-reponse>) 
  Where
  <query-response> is the value returned when `query` is posed to `endpoint`
  via an HTTP GET call which may be informed by `http-req'
  <render-results> := fn(<query response>) -> e.g. true/false 
  (for an ask query)
  <http-req> := {?param...}
  <param> is anything described in <https://github.com/dakrone/clj-http>
  Though :form-params :accept :saved-request? will be overridden.
  "
  ([endpoint query render-results http-req]
   {
    :pre [(re-find #"http" endpoint)
          (string? query)
          (fn? render-results)
          ]
    }
   (let [response (http/get endpoint 
                            (merge http-req
                                   {:query-params {:query query}
                                    :accept "application/sparql-results+json"
                                    :debug true
                                    :save-request? true
                                    }))
         content-type (:Content-Type (:headers response))
         
         ]
     (assert (re-find #"json" content-type))
     (case (:status response)
       200  (render-results (json/read-str (:body response)))
       400 (log/info (str "Code 400:" response))
       :default (throw (Error. (str "No handler for status " 
                                    (:status response)))))))
  
  ([endpoint query render-results]
   ;; use empty http-req parameter by default
   (sparql-query endpoint query render-results {})))


(def type-mapper
  "maps names to xsd datatypes"
  (. org.apache.jena.datatypes.TypeMapper getInstance))

(defn parse-xsd-value 
  "
  Returns <value> for `literal`
  Where
  <literal> is a literal value typically from a binding acquired from a 
    select query.
  <value> is an instance of the xsd datatype specified for <literal>
  "
  [literal]
  (print "literal:" literal)
  (let [type (.getTypeByName type-mapper (get literal "datatype"))
        ]
    (if type
      (.parse type (get literal "value"))
      (get literal "value"))))

(defn maybe-parse-xsd
  "
  Returns <parsed value>, if `literal` is an xsd value or <value> otherwise.
  Where
  <literal> := {:value <value> :datatype <datatype> }, part of a SPARQL 
    binding to a variable
  <parsed-value> is e.g. an int, float, etc. as is typically the case
    with standard xsd classes.
  <value> is a string mapped to :value in <literal>
  "
  [literal]
  (if (get literal "datatype")
    (parse-xsd-value literal)
    (get literal "value")))


(defn sparql-select
  "
  Returns <bindings> for `query` posed to `endpoint`, handling
    literals according to `handle-literal`
  Where
  <query> := a SPARQL SELECT query
  <endpoint> the URL string of a SPARQL endpoint
  <handle-literal> := fn[literal-value] -> <parsed-value>
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
  ([endpoint query handle-literal]
   {
    :pre [(re-find #"http"  endpoint)
          (string? query)
          (re-find #"(?i)SELECT" query)
          ]
    :post [(if (not (vector? %)) (let [] (log/info %) false) true)]
    }
   (letfn [(render-select-results [http-response-body]
             ;; Returns [{<key> <value>, ...}, ...]
             ;; (log/info (str "body:"  http-response-body))
             (letfn [(render-binding [binding] ;; (fn [binding] 
                       (into {} 
                             (map (fn [[k v]]
                                    ;; (print "v:" v)
                                    [k
                                     (case (get v "type")
                                       "literal" (handle-literal v)
                                       v)])
                                  binding)))
                     ]
               (into []
                     (map render-binding
                          (-> http-response-body
                              (get "results")
                              (get "bindings"))))))
           ]
     (log/info query)
     (sparql-query endpoint query render-select-results)))

  ([endpoint query]
   (sparql-select endpoint query maybe-parse-xsd)))



(defn sparql-ask
  "
  Returns boolean value per `query` posed to `endpoint`, through an
  http call possibly informed by `http-req`.
  Where
  <query> is a SPARQL ASK query
  <endpoint> is a SPARQL endpoint
  <http-req> := {?param...}
  <param> is anything described in <https://github.com/dakrone/clj-http>
  Though :form-params :accept :saved-request? will be overridden.

  "
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
                 (get "boolean")))
           ]
     (sparql-query endpoint query render-ask-results)))

  ([endpoint query]
   (sparql-ask endpoint query {})))
  
(defn sparql-select
  "
  Returns <bindings> for `query` posed to `endpoint`, handling
    literals according to `handle-literal`
  Where
  <query> := a SPARQL SELECT query
  <endpoint> the URL string of a SPARQL endpoint
  <handle-literal> := fn[literal-value] -> <parsed-value>
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
  ([endpoint query handle-literal]
   {
    :pre [(re-find #"http"  endpoint)
          (string? query)
          (re-find #"(?i)CONSTRUCT" query)
          ]
    :post [(if (not (vector? %)) (let [] (log/info %) false) true)]
    }
   (letfn [(render-select-results [http-response-body]
             ;; Returns [{<key> <value>, ...}, ...]
             ;; (log/info (str "body:"  http-response-body))
             (letfn [(render-binding [binding] ;; (fn [binding] 
                       (into {} 
                             (map (fn [[k v]]
                                    ;; (print "v:" v)
                                    [k
                                     (case (get v "type")
                                       "literal" (handle-literal v)
                                       v)])
                                  binding)))
                     ]
               (into []
                     (map render-binding
                          (-> http-response-body
                              (get "results")
                              (get "bindings"))))))
           ]
     (log/info query)
     (sparql-query endpoint query render-select-results)))

  ([endpoint query]
   (sparql-select endpoint query maybe-parse-xsd)))




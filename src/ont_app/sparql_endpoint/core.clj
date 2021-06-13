(ns ont-app.sparql-endpoint.core
  "Functions to support interacting with a SPARQL endpoint.
  See also `https://www.w3.org/TR/sparql11-query`.
  "
  (:import 
   [org.apache.jena.datatypes.xsd XSDDatatype]
   [org.apache.jena.datatypes TypeMapper]
   [org.apache.jena.sparql.core Prologue]
   [org.apache.jena.query QueryFactory Query]
   [org.apache.jena.iri IRI IRIFactory]
   
   )
  (:require
   [clj-http.client :as http]
   [clojure.string :as s]
   [clojure.data.json :as json]
   [taoensso.timbre :as log]
   [ont-app.vocabulary.core :as voc]
   [ont-app.vocabulary.lstr :refer [->LangStr lang]]
   ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URI/IRI utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn angle-bracket-uri 
  "returns < `s`> if it matches the scheme for a URI, else returns `s`."
  ([s]
   {:pre [(string? s)]
    :post [#(string? %)]
    }
   (if (re-matches #"^(http:|https:|file:).*" s)
     (str "<" s ">")
     s)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-prologue
  "Returns [`base`, `uri-to-qname`, `qname-to-uri`] parsed from the 
    `prologue` to `query`
  Where
  - `base` is a URI string for the base of `query`
  - `uri-to-qname` := fn[`uri`] -> `quickname`, or `uri` if there was no matching
    prefix in `prolog`
  - `qname-to-uri` := fn[`qname`] -> `uri`   or `qname` if there
    was no matching prefix in `prologue`
  - `uri` is typically a URI with a prefix defined in the `prologue`, but
    may be any string
  - `qname` is typically a qname with a prefix defined in `prologue`,
    but may be any string
  - `prologue` is the prologue parsed from `query`, for which see
    spex at `https://www.w3.org/TR/sparql11-query/`.
  "
  [^String query]
  {
   :pre [(string? query)]
   :post [(let [[base, u-to-q, q-to-u] %]
            (and (string? base)
                 (fn? u-to-q)
                 (fn? q-to-u)))]
   }
  (let [unquote (fn [s] (s/replace s #"[<>]" ""))
        q ^Query(. QueryFactory create query)
        p ^Prologue(.getPrologue q)
        base (.getBaseURI p)
        ]
    ;; return [<base> <uri to quickname> <quickname to uri>]
    [base
     (fn[u] (let [qname (.shortForm p (unquote u))]
              (if (not= qname u)
                qname
                u)))
     (fn[u] (or (.expandPrefixedName p u) u))
     ]))
        
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sparql-update
  "Side Effect: Modifies the contents of `endpoint` per the update query 
  `update`, possibly informed by http parameters  `http-req'
  Returns the string returned by `endpoint` if successful.
  Where
  - `endpoint` is a SPARQL update endpoint
  - `update` is a SPARQL update expression
  - `http-req` := {?param...}
  - `param` is anything described in `https://github.com/dakrone/clj-http`
     Though :form-params will be overridden.
     This may be used for authentication parameters for example.
     Default parameters are {:cookie-policy :standard, :accept text/plain}
  "
  ([endpoint update]
   (sparql-update endpoint update {}))
  
  ([endpoint update http-req]
   {
    :pre [(string? update)
          (re-find #"http" (str endpoint))
          (map? http-req)
          ]
    }
   (let [response (http/post endpoint (merge (merge {:cookie-policy :standard
                                                     :accept "text/plain"
                                                     }
                                                    http-req)
                                             {:form-params {:update update}
                                              }))

         ]
     (if (<= 200 (:status response) 299)
       (:body response)
       ;; this is actually moot. clj-http will raise an error
       (throw (Error. (str "HTTP Error "
                           (:status response)
                           (:reason-phrase response)
                           (:body response))))))))



(defn sparql-query
  "
  Returns output of <response-body> for SPARQL `query`
    posed to `endpoint`, possibly informed by `http-req`
  Where
  - `response-body` is the body of the response to `query`, posed to `endpoint`
    via an HTTP GET call which may be informed by `param`s in `http-req`
  - `http-req` := {`param` `spec`,...}, default is {}
  - `param` is anything described in ``https://github.com/dakrone/clj-http``
    typically :debug or authentication parameters
    :query-params will be overridden.
    {:cookie-policy :standard} will be asserted by default
  "
  ([endpoint query]
   (sparql-query endpoint query {}))
  
  ([endpoint query http-req]
   {
    :pre [(re-find #"http" endpoint)
          (re-find #"(?i)ASK|SELECT|CONSTRUCT" query)
          ]
    }
   (let [response (http/get endpoint 
                            (merge (merge {:cookie-policy :standard}
                                          http-req)
                                   {:query-params {:query query}
                                    }))
         ]
     (if (<= 200 (:status response) 299)
       (:body response)
       ;; this is actually moot. clj-http will raise an error
       (throw (Error. (str "HTTP Error "
                           (:status response)
                           (:reason-phrase response)
                           (:body response))))))))




(def ^TypeMapper type-mapper
  "Maps datatype names to xsd datatypes"
  (. TypeMapper getInstance))

(defn ^ont_app.vocabulary.lstr.LangStr literal->LangStr [literal]
  (if-let [lang-tag (literal "xml:lang")
           ]
    (->LangStr (literal "value") lang-tag)))

(defn meta-tagged-literal
  "Returns a reified object s.t. ^{:type `type`, `k` `v`, ...} Object.toString(this) -> value
  Where
  - `literal` is a sparql binding value-map s.t.
    {type literal, `k` `v`, value `value` ...}
  - `type` is one of ::langString ::datatype
  - `k` is any key in `literal` except 'type' and 'value', e.g. xml:lang or datatype
  - `v` is the value associated with `k` in `literal`
  NOTE: type ::langString keys to a print-method  and the function
    read-langString,  supporting the #langString reader macro
  "
  [literal]
  (with-meta
    (reify Object
      (toString [x] (get literal "value")))
    (dissoc literal "value")))

(defn parse-xsd-value 
  "
  Returns `translated-value` for `literal`
  Where
  - `literal` is a sparql binding value-map s.t.
    {type literal, datatype `datatype`, value `value` ...}
  - `translated-value` is an instance of the xsd datatype associated with
    `datatype` specified for `value`, or `value` if no translation can be
    found.
  - `datatype` is a string indicating the datatype associated with `value`,
    which may be an xsd datatype
  "
  [literal]
  (let [expand-xsd-prefix (fn[s]
                            (s/replace
                             s
                             #"xsd:"
                             "http://www.w3.org/2001/XMLSchema#"))
        ^XSDDatatype
        type (.getTypeByName
              type-mapper
              (expand-xsd-prefix
               (get literal "datatype")))
        ]
    (if type
      (.parse type (get literal "value"))
      (meta-tagged-literal literal))))


(defn ^String xsd-type-uri
  "Returns xsd URI for (type `x`), or nil if there is no mapping in `type-mapper`.
  Example: [1] -> 'http://www.w3.org/2001/XMLSchema#long'
  Where:
  - `x` is any value.
"
  ([x]
   (if-let [mapping (.getTypeByClass
                     type-mapper
                     (if (or (inst? x)
                             (instance? java.util.Calendar x))
                       org.apache.jena.datatypes.xsd.XSDDateTime
                       (type x)))
            ]
     (-> mapping
         (.getURI)))))



(def default-translators
  "A map with keys :uri :lang :datatype, each mapping to `translator`
  Where
  - `translator` := (fn[var-value]) -> `translated value`
  - `var-value` :={'value' `value`
                 'type' 'uri' | 'literal'
                 ...maybe...
                 'xml:lang' `lang` (if literal)
                 'datatype' `datatype` (if literal)
                }
  "
  {:uri (fn[b] (get b "value"))
   :lang literal->LangStr
   :datatype parse-xsd-value
   :bnode (fn [b] (get b "value"))
   })



(defn simplify
  "Returns {`var-keyword` `translated-value`, ...} for each `var` in `var-map`,
    translated according to `translators` (default `default-translators`)
  Where
  - `var-map` := {`var` `var-value`...}
  - `var` is a string typically corresponding to a variable in a SELECT query
  - `var-value` := m s.t. (keys m) :~ #{type value xml:lang datatype},
    per the SPARQL 1.1 specification for SELECT queries.
  - `var-keyword` is keyword corresponding to `var`
  - `translated-value` is `value` from `var-value`, translated using `translators`
  - `translators` is a map with keys in #{:uri :lang :datatype}, each of which
    maps to a (fn[var-value])-> `translated-value`, depending on whether
    `var-value` represents a URI, a literal with a language tag, or a literal
    with a specified datatype. Default is simply to render the 'value' field.
  - Note: see also `https://www.w3.org/TR/sparql11-results-json/`
  "
  ([var-map]
   (simplify default-translators var-map)
   )
  ([translators var-map]
   (let [render-value (fn[var-value]
                        (let [translator
                              (cond
                                (= (get var-value "type") "uri")
                                (:uri translators)
                                (and (= (get var-value "type") "literal")
                                     (contains? var-value "xml:lang"))
                                (:lang translators)
                                (and (= (get var-value "type") "literal")
                                     (contains? var-value "datatype"))
                                (:datatype translators)
                                (and (= (get var-value "type") "bnode"))
                                (:bnode translators)
                                :default
                                (fn[b] (get b "value")))]
                          (translator var-value)))
         
         render-binding (fn [[var var-value]]
                          [(keyword var)
                           (render-value var-value)])
         ]

     (into {} (map  render-binding var-map)))))


(defn update-translators
  "Returns `translator`', for `k`, per `vfn`
  Where
  - `translators` := m s.t. (keys m) #= `keys`
  - `k` is in `keys`
  - `vfn` := fn [`value`] -> `translated-value`
  - `keys` := #{:uri :lang :datatype :bnode} 
  "
  [translators k vfn]
  (merge translators
         {k (fn[var-value]
              (vfn (get var-value "value")))
          }))

(defn make-simplifier
  [translators]
  (fn [var-map]
    (simplify translators var-map)))

(defn simplifier-for-prologue
  "Returns a function (fn[`var-map`] -> {`var-keyword` `translated-value`, ...}
    for each `var` in `var-map`, transating URIs into qnames derived from the
    prologue to `query`, and otherwise using `translators` (default
    `default-translators`)"
  ([query]
   (simplifier-for-prologue default-translators query)
   )
  ([translators query]
   (let [[_ q-namer _] (parse-prologue query)]
     (make-simplifier (update-translators translators :uri q-namer)))))


(def simplifier-with-kwis
  (make-simplifier (update-translators default-translators
                                       :uri voc/keyword-for)))

(defn sparql-select
  "
  Returns `bindings` for `query` posed to `endpoint`, using an HTTP call
    informed by `http-req`
  Where
  - `query` := a SPARQL SELECT query
  - `endpoint` the URL string of a SPARQL endpoint
  - `bindings` := [`binding` , ...]
  - `binding` := {`var` `var-value`, ...}
  - `var` is a variable specified in `query`
  - `var-value` := `uri-value` or `literal-value`
  - `uri-value` := {type uri, value `uri`}
  - `literal-value` :=  {type literal,
                       value `value`,
                       maybe datatype `datatype`,
                       maybe xml:lang `lang`
                      }
  - Note: see also `https://www.w3.org/TR/sparql11-results-json/`
  "
  ([endpoint query]
   (sparql-select endpoint query {}))
  
  ([endpoint query http-req]
   {
    :pre [(re-find #"http"  endpoint)
          (string? query)
          (re-find #"(?i)SELECT" query)
          ]
    }
     ;; (log/debug query)
     (-> (sparql-query endpoint
                       query
                       (merge http-req
                              {:accept "application/sparql-results+json"}))
         (json/read-str)
         (get "results")
         (get "bindings"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ASK
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sparql-ask
  "
  Returns boolean value per `query` posed to `endpoint`, through an
  HTTP call possibly informed by `http-req`.
  Where
  - `query` is a SPARQL ASK query
  - `endpoint` is a SPARQL endpoint
  - `http-req` := {`param` `spec`, ...}
  - `param` is anything described in `https://github.com/dakrone/clj-http`
    Though :form-params will be overridden.

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
   (-> (sparql-query endpoint
                     query
                     (merge http-req
                            {:accept "application/sparql-results+json"}))
       (json/read-str)                 
       (get "boolean"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CONSTRUCT
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sparql-construct
  "
  Returns `expression` for `query` posed to `endpoint`, possibly informed
    by `http-req`
  Where
  - `query` := a SPARQL CONSTRUCT query
  - `endpoint` the URL string of a SPARQL endpoint
  - `http-req` := {?param...}
  - `param` is anything described in `https://github.com/dakrone/clj-http`
    Though :form-params will be overridden.
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
                        http-req))))




(ns sparql-endpoint.core-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [sparql-endpoint.core :as sparql]))

(log/set-level! :warn)

(def wikidata-endpoint "https://query.wikidata.org/bigdata/namespace/wdq/sparql")



(def prefixes
  "
  PREFIX owl: <http://www.w3.org/2002/07/owl#>
  PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
  PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
  PREFIX wdt: <http://www.wikidata.org/prop/direct/>
  PREFIX schema: <http://schema.org/>
  PREFIX wd: <http://www.wikidata.org/entity/>
  PREFIX eg: <http://example.com/>
")

(defn prefix 
  "Returns `q` prepended by `prefixes`"
  ([q]
   (str prefixes q)))


(deftest ask-test
  (testing "An all-inclusive SPARQL ASK query posed to wikidata should
  return _true_"
    (let [query "Ask Where {?s ?p ?o}"
          ]

    (is (= (sparql/sparql-ask wikidata-endpoint query)
           true)))))

(deftest bad-query-test
  (testing "An invalid SPARQL query posed to wikidata should raise an error"
    (let [query "Go ask your mother"
          ]
      (is (thrown? Exception (sparql/sparql-ask wikidata-endpoint query))))))

(deftest select-test
  (testing "A SPARQL SELECT query for 'human' posed to wikidata should
  return wd:Q5 amongst its answers."
    (let [query (prefix "Select ?q Where {?q rdfs:label \"human\"@en}")
          result (sparql/sparql-select wikidata-endpoint  query)
          ]
      (is (= (some #(= % "http://www.wikidata.org/entity/Q5")
                   (map (fn [binding] (get-in binding ["q" "value"]))
                        result))
             true)))))


(deftest construct-test
  (testing "A SPARQL CONSTRUCT query for 'human' posed to wikidata should return a string of turtle with 'Q5' as a substring"
    (let [query (prefix
                 "Construct {?q a eg:Human} Where {?q rdfs:label \"human\"@en}")
          result (sparql/sparql-construct wikidata-endpoint  query)
          ]
      (is (= (re-find #"Q5" result)
             "Q5")))))

  
(deftest parse-prolog-test
  (testing "`parse-prolog` should return the base and a pair of
inverse mapping functions between q-names and uris, for each prefix
the prolog of the query being parsed
"
    (let [query "BASE <http://example.org/>
                 PREFIX eg: <http://example.com/> 
                 Select * where {?s ?p ?o.}"
          [base, u-to-q, q-to-u] (sparql/parse-prologue query)
          ]
      (is (= base "http://example.org/"))
      (is (= (u-to-q "<http://example.com/blah>") "eg:blah"))
      (is (= (u-to-q "http://example.com/blah") "eg:blah"))
      (is (= (q-to-u  "eg:blah") "http://example.com/blah"))
      (is (= (q-to-u  "blah") "blah"))
      (is (= (u-to-q  "blah") "blah")))))

(deftest simplify-test
  (testing "`simplify` when mapped over the output of `sparql-select` should return simplified maps of the results"
    (let [uri-query "
# What's the schema.org equivlent of Q5?
Select ?schemaOrgEquivalent
Where 
{
  wd:Q5 wdt:P1709 ?schemaOrgEquivalent. 
  Filter Regex(Str(?schemaOrgEquivalent), \"schema.org\")
} "

          label-query "
# What's the English word for Q5?
Select ?label 
Where 
{
  wd:Q5 rdfs:label ?label.
  Filter (Lang(?label) = \"en\")
} "
          datatype-query "
# What is Obama's date of birth?
Select ?dob
Where 
{
  wd:Q76 wdt:P569 ?dob.
} "

          ]
      ;; Labels by default just return the string value...
      (is (= (map sparql/simplify
                  (sparql/sparql-select wikidata-endpoint (prefix label-query)))
             '({:label "human"})))

      ;; URIs are angle-braced by default...
      (is (= (map sparql/simplify
                  (sparql/sparql-select wikidata-endpoint (prefix uri-query)))
             '({:schemaOrgEquivalent "http://schema.org/Person"})))

      ;; xsd values should be parsed into actual Java objects...
      (is (= (let [bindings (vec (map sparql/simplify
                                      (sparql/sparql-select
                                       wikidata-endpoint
                                       (prefix datatype-query))))
                   dob (:dob (nth bindings 0))
                   ;; <dob> is an XSDDateTime
                   ]
               (.getYears dob)) 
             1961))
      ;; ... See https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/datatypes/xsd/XSDDateTime.html

      )))

(deftest simplifier-for-prologue-test
  (testing "The function returned by `simplifier-for-prologue` when mapped over the output of `sparql-select` should render a qnames URIs for which there is a prefix declaration."
    (let [query "
# All the Q-numbers called 'human' in English

PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX wd: <http://www.wikidata.org/entity/>
Select ?q 
Where 
{
  ?q rdfs:label \"human\"@en
}"
          ]
      (is (contains? (set (map (sparql/simplifier-for-prologue query)
                               (sparql/sparql-select
                                  wikidata-endpoint
                                  query)))
                     {:q "wd:Q5"})))))


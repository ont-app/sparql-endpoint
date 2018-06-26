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

(defn prefix [q]
  "Returns `q` prepended by `prefixes`"
  (str prefixes q))


(deftest ask-test
  (testing "An all-inclusive SPARQL ASK query posed to wikidata should
  return _true_"
    (let [query "Ask Where {?s ?p ?o}"
          endpoint wikidata-endpoint
          ]

    (is (= (sparql/sparql-ask endpoint query)
           true)))))

(deftest bad-query-test
  (testing "An invalid SPARQL query posed to wikidata should raise an error"
    (let [query "Go ask your mother"
          endpoint wikidata-endpoint
          ]
    (is (thrown? Exception (sparql/sparql-ask endpoint query))))))

(deftest select-test
  (testing "A SPARQL SELECT query for 'human' posed to wikidata should
  return wd:Q5 amongst its answers."
    (let [query (prefix "Select ?q Where {?q rdfs:label \"human\"@en}")
          endpoint wikidata-endpoint
          result (sparql/sparql-select endpoint  query)
          ]
      (is (= (some #(= % "http://www.wikidata.org/entity/Q5")
                   (map (fn [binding] (get-in binding ["q" "value"]))
                        result))
             true)))))


(deftest construct-test
  (testing "A SPARQL CONSTRUCT query for 'human' posed to wikidata should return a string of turtle with 'Q5' as a substring"
    (let [query (prefix
                 "Construct {?q a eg:Human} Where {?q rdfs:label \"human\"@en}")
          endpoint wikidata-endpoint
          result (sparql/sparql-construct endpoint  query)
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
      (is (= base "<http://example.org/>"))
      (is (= (u-to-q "<http://example.com/blah>") "eg:blah"))
      (is (= (u-to-q "http://example.com/blah") "eg:blah"))
      (is (= (q-to-u  "eg:blah") "<http://example.com/blah>"))
      (is (= (q-to-u  "blah") "blah"))
      (is (= (u-to-q  "blah") "blah")))))


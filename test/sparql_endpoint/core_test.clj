(ns sparql-endpoint.core-test
  (:require [clojure.test :refer :all]
            [sparql-endpoint.core :as sparql]))

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

(deftest succeed []
  (testing "This should succeed"
    (is (= 1 1))))

(deftest fail []
  (testing "This should fail"
    (is (= 0 1))))

(deftest ask-test
  (testing "Test a sparql ask query"
    (let [query "Ask Where {?s ?p ?o}"
          endpoint wikidata-endpoint
          ]

    (is (= (sparql/sparql-ask endpoint query)
           true)))))



(deftest select-test
  (testing "Test a sparql select query"
    (let [query (prefix "Select ?q Where {?q rdfs:label \"human\"@en}")
          endpoint wikidata-endpoint
          result (sparql/sparql-select endpoint  query)
          ]
      (is (= (some #(= % "http://www.wikidata.org/entity/Q5")
                   (map (fn [binding] (get-in binding ["q" "value"]))
                        result))
             true)))))

(defn construct []
  (let [query (prefix "Construct {?q a eg:Human} Where {?q rdfs:label \"human\"@en}")
        endpoint wikidata-endpoint
        ]
    (sparql/sparql-select endpoint  query)))

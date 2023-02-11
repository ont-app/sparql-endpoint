(ns ont-app.sparql-endpoint.core-test
  {:vann/preferredNamespacePrefix "sparql-endpoint-test"
   :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/ont-app/sparql-endpoint/test#"
   }
  (:require [clojure.test :refer :all]
            [clojure.repl :refer [apropos]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [taoensso.timbre :as log]
            [ont-app.vocabulary.core :as voc]
            [ont-app.vocabulary.wikidata :as wd]
            [ont-app.sparql-endpoint.core :as sparql]
            ))

(log/set-level! :info)


(def wikidata-endpoint wd/sparql-endpoint)


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
# What's the dbpedia.org equivlent of Q5?
Select ?exactMatch
Where 
{
  wd:Q5 wdt:P2888 ?exactMatch. # foaf equivalent
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

      ;; default lang tags by default just return the string:
      (is (=
           (first
            (map :label
                 (map sparql/simplify
                      (sparql/sparql-select wikidata-endpoint
                                            (prefix label-query)))))
           #voc/lstr "human@en"))

      ;; URIs are angle-braced by default...
      (is (some
           (fn [b] (= b {:exactMatch "http://xmlns.com/foaf/spec/Person"}))
           (map sparql/simplify
                  (sparql/sparql-select wikidata-endpoint (prefix uri-query)))
             ))

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

(def human-query "
# All the Q-numbers called 'human' in English

PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX wd: <http://www.wikidata.org/entity/>
Select ?q 
Where 
{
  ?q rdfs:label \"human\"@en
}"
  )

(deftest simplifier-for-prologue-test
  (testing "The function returned by `simplifier-for-prologue` when mapped over the output of `sparql-select` should render a qnames URIs for which there is a prefix declaration."
    (let [
          ]
      (is (contains? (set (map (sparql/simplifier-for-prologue human-query)
                               (sparql/sparql-select
                                  wikidata-endpoint
                                  human-query)))
                     {:q "wd:Q5"})))))

(deftest xsd-type-uri-issue-1
  (testing "xsd-type-uri"
    (is (= (sparql/xsd-type-uri 1)
           "http://www.w3.org/2001/XMLSchema#long"))
    (is (= (sparql/xsd-type-uri 1.0)
           "http://www.w3.org/2001/XMLSchema#double"))
    (is (= (sparql/xsd-type-uri #inst "2020-02-14")
           "http://www.w3.org/2001/XMLSchema#dateTime"))))

(deftest simplifier-with-kwi-test
  (let []
    (is (contains? 
         (->> 
          (sparql/sparql-select
           wikidata-endpoint
           human-query)
          (map sparql/simplifier-with-kwis)
          (set))
         {:q :wd/Q5}))))



(def test-update-endpoint nil)

(deftest test-updates
  (if-let [endpoint (or test-update-endpoint
                        (System/getenv "ONT_APP_TEST_UPDATE_ENDPOINT"))
           ]

    
    (let [update-endpoint (str endpoint "update")
          query-endpoint (str endpoint "query")
          test-graph (voc/uri-for :sparql-endpoint-test/test-graph)
          insert-query (format "INSERT {GRAPH <%s> {<%s> <%s> <%s>.}} WHERE {}"
                            test-graph
                            (voc/uri-for :sparql-endpoint-test/A)
                            (voc/uri-for :sparql-endpoint-test/B)
                            (voc/uri-for :sparql-endpoint-test/C))
          ]
      (log/info (str "Using update endpoint " update-endpoint))
      (log/info (str "Using query endpoint " query-endpoint))
      (let [
            update-result (sparql/sparql-update update-endpoint insert-query)
            select-query "
Prefix : <http://rdf.naturallexicon.org/ont-app/sparql-endpoint/>
Select *
where
{
  Graph ?g
  {?s ?p ?o}
}
"
            select-result (map sparql/simplifier-with-kwis
                               (sparql/sparql-select query-endpoint select-query
                                                     ))
            ]
        (is (= #{{:s :sparql-endpoint-test/A,
                  :p :sparql-endpoint-test/B,
                  :o :sparql-endpoint-test/C,
                  :g :sparql-endpoint-test/test-graph}}
               
               (set select-result)))
        (log/info (format "Dropping graph <%s>" test-graph))
        (sparql/sparql-update update-endpoint (format "DROP GRAPH <%s>" test-graph))
        )
      )
    ;; else no endpoint
    (log/warn "Environment variable ONT_APP_TEST_UPDATE_ENDPOINT not specified (skipping update tests).")))


(comment
  (def test-update-endpoint "http://localhost:3030/test-dataset/")
  (def update-endpoint "http://localhost:3030/test-dataset/update")
  (def query-endpoint "http://localhost:3030/test-dataset/query")
  (def test-graph (voc/uri-for :sparql-endpoint-test/test-graph))
  (sparql/sparql-update update-endpoint "DROP ALL")
  (def insert-query (format "INSERT {GRAPH <%s> {<%s> <%s> <%s>.}} WHERE {}"
                            test-graph
                            (voc/uri-for :sparql-endpoint-test/A)
                            (voc/uri-for :sparql-endpoint-test/B)
                            (voc/uri-for :sparql-endpoint-test/C)))
                            
  (time (sparql/sparql-update update-endpoint insert-query))
  (time (sparql/sparql-select query-endpoint "Select * where {Graph ?g {}}"))
  (def select-query "
Prefix : <http://rdf.naturallexicon.org/ont-app/sparql-endpoint/>
Select *
where
{
  Graph ?g
  {?s ?p ?o}
}
")
  (time (map sparql/simplifier-with-kwis(sparql/sparql-select query-endpoint "Select * where {Graph ?g {?s ?p ?o}}")))

  )

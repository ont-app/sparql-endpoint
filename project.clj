(defproject sparql-endpoint "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns sparql-endpoint.core}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.7.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.apache.jena/jena-core "3.6.0"]
                 [org.apache.jena/jena-arq "3.6.0"]
                 [org.apache.jena/jena-iri "3.6.0"]
                 ])

(defproject ont-app/sparql-endpoint "0.1.1"
  :description "Utilities for interfacing with a sparql endpoint in clojure"
  :url "https://github.com/ont-app/sparql-endpoint/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns sparql-endpoint.core}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [clj-http "3.10.0"]
                 ;; logging...
                 [com.taoensso/timbre "4.10.0"]
                 ;; fixes a warning in logging...
                 [com.fzakaria/slf4j-timbre "0.3.19"]
                 ;; included per docs in slf4j-timbre...
                 [org.slf4j/log4j-over-slf4j "1.7.30"]
                 [org.slf4j/jul-to-slf4j "1.7.30"]
                 [org.slf4j/jcl-over-slf4j "1.7.30"]
                 ;; makes light use of jena to parse SPARQL, XSD values, etc...
                 [org.apache.jena/jena-core "3.14.0"]
                 [org.apache.jena/jena-arq "3.14.0"]
                 [org.apache.jena/jena-iri "3.14.0"]
                 ]
  :plugins [[lein-codox "0.10.6"]
            ]
  :codox {:output-path "doc"}
  :profiles {:uberjar {:aot :all}}
  )

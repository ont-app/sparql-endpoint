(defproject ontapp/sparql-endpoint "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns sparql-endpoint.core}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.9.0"]
                 ;; logging...
                 [com.taoensso/timbre "4.10.0"]
                 ;; fixes a warning in logging...
                 [com.fzakaria/slf4j-timbre "0.3.8"]
                 ;; included per docs in slf4j-timbre...
                 [org.slf4j/log4j-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]
                 ;; makes light use of jena to parse SPARQL, XSD values, etc...
                 [org.apache.jena/jena-core "3.6.0"]
                 [org.apache.jena/jena-arq "3.6.0"]
                 [org.apache.jena/jena-iri "3.6.0"]
                 ])

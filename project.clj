
(defproject ont-app/sparql-endpoint "0.1.3"
  :description "Utilities for interfacing with a sparql endpoint in clojure"
  :url "https://github.com/ont-app/sparql-endpoint/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns sparql-endpoint.core}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.3.1"]
                 [clj-http "3.12.2"]
                 ;; logging...
                 [com.taoensso/timbre "5.1.2"]
                 ;; fixes a warning in logging...
                 [com.fzakaria/slf4j-timbre "0.3.21"
                  :exclusions [[org.slf4j/slf4j-api]]]
                 ;; included per docs in slf4j-timbre...
                 [org.slf4j/log4j-over-slf4j "1.7.31"] ;;"1.7.30"]
                 [org.slf4j/jul-to-slf4j "1.7.31"]
                 [org.slf4j/jcl-over-slf4j "1.7.31"]
                 ;; makes light use of jena to parse SPARQL, XSD values, etc...
                 [org.apache.jena/jena-core "3.14.0"] 
                 [org.apache.jena/jena-arq "3.14.0"]
                 [org.apache.jena/jena-iri "3.14.0"]
                 ;; ... see Appendix below for notes about jena versioning
                 [ont-app/vocabulary "0.1.3"
                  :exclusions
                  [
                   [com.google.code.findbugs/jsr305]
                   [com.google.errorprone/error_prone_annotations]
                   [lein-doo]
                   [org.clojure/clojurescript]
                  ]
                 ]]
  :plugins [[lein-codox "0.10.6"]
            ]
  :codox {:output-path "doc"}
  :profiles {:uberjar {}}
  )

;; APPENDIX
;;   Using Java 1.8
  
;;   using [org.apache.jena/jena-* "4.0.0"] raises the following error:

;;   org/apache/jena/datatypes/xsd/XSDDatatype has been compiled by a more recent version of the Java Runtime (class file version 55.0), this version of the Java Runtime only recognizes class file versions up to 52.0

;;   Holding off on the upgrade for now.


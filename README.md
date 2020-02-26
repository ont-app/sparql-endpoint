

# Introduction

sparql-endpoint provides utilities for interfacing with [SPARQL 1.1](https://www.w3.org/TR/sparql11-query/)
endpoints in clojure.



# Installation

`sparql-endpoint` is available as a Maven artifact from clojars. 

[![Clojars Project](https://img.shields.io/clojars/v/ont-app/sparql-endpoint.svg)](https://clojars.org/ont-app/sparql-endpoint)


Additional documentation is provided at https://cljdoc.org/d/ont-app/sparql-endpoint/0.1.1.

Require thus:
```
(ns my.namespace
  (:require
    [ont-app.sparql-endpoint.core :as endpoint]
    ))
```

# Functions


## Functions that interact with SPARQL endpoints

These involve POSTs to an [update endpoint](https://www.w3.org/TR/sparql11-update/) and GETs to a [query
enpoint](https://www.w3.org/TR/sparql11-query/). There are special functions for ASK, SELECT and CONSTRUCT
queries. Each of these take mandatory `endpoint` and `query`
arguments, and an optional `http-req` argument.



### Mandatory arguments: `endpoint` and `query`

All of the basic query and update functions take two mandatory arguments: 

`endpoint` is the URL of a SPARQL endpoint

`query` is a string in an appropriate format for ASK, SELECT,
CONSTRUCT, or one of the UPDATE operations.



### Optional argument: `http-req`

HTTP calls are done through [clj-http](https://github.com/dakrone/clj-http). There is a third optional
`http-req` argument which may include additional HTTP request
parameters.

For example if `endpoint`requires authentication, you may specify
`{:basic-auth "myUserName:myPassword"}`

`{:cookie-policy :standard`} is asserted by default, but this can
be overridden. The `:query-params` parameter is reserved, as it is
needed to specify the query to the endpoint.


### sparql-ask

This function takes an endpoint and a SPARQL ASK query and returns a boolean:

    (use 'ont-app.sparql-endpoint.core)
    (sparql-ask 
        "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
        "ASK WHERE {wd:Q5 rdfs:label \"human\"@en}")
    ;; --> true

### sparql-select

This function takes as its `query` parameter a SPARQL SELECT query:

    (use 'ont-app.sparql-endpoint.core)
    (let [query "
    # What is the English name for Q5?
    PREFIX rdfs: `
    PREFIX wd: <http://www.wikidata.org/entity/>
    SELECT ?enLabel
    WHERE
    {
      wd:Q5 rdfs:label ?enLabel.
      Filter (Lang(?enLabel) = \"en\")
    }"
      ]
      (sparql-select 
          "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
          query)
    ;; => [{"enLabel" {"xml:lang" "en", "type" "literal", "value" "human"}}]

The bindings returned are direct translations of the JSON returned by
the endpoint. These can be mapped by more expressive `simplifiers`,
described below.


### sparql-construct

This function takes a SPARQL CONSTRUCT query as its query parameter
and returns a string of [turtle](https://www.w3.org/TR/turtle/) describing the results.

    (use 'ont-app.sparql-endpoint.core)
    (let [query "
    # Things called 'human'
    PREFIX eg: <http://example.com/>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX wd: <http://www.wikidata.org/entity/>
    
    CONSTRUCT
    {
      ?human a eg:Human.
    }
    WHERE
    {
      ?human rdfs:label \"human\"@en.
    }"
      ]
      (sparql-construct       
        "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
         query))
    
    ;; -> "
    @prefix eg: <http://example.com/> .
    @prefix wd: <http://www.wikidata.org/entity/> .
    
    wd:Q823310 a eg:Human .
    
    wd:Q20094897 a eg:Human .
    
    wd:Q26190966 a eg:Human .
    
    wd:Q5 a eg:Human .
    "

### sparql-update

This function POSTS its query parameter (CREATE, INSERT, DELETE, etc)
to the specified SPARQL update endpoint, and returns the plain text
response.

## Simplifiers

By default the output of `sparql-select` is parsed JSON of raw
output of the endpoint, using [the specification described by W3C](https://www.w3.org/TR/sparql11-results-json/). 

    {'value' <value>
     'type' 'uri' | 'literal'
     ;;...maybe...
     'xml:lang' <lang> (if literal)
     'datatype' <datatype> (if literal)
    }

It is usually convenient to transform these bindings into simpler
representations. Hence the functions `simplify` and
`simplifier-for-prologue`, described below.

### simplify

The function `simplify` will take a result binding and return a simplified map `{<var> <value>...}`. This would typically be done in the context of a map function:


    (use 'ont-app.sparql-endpoint.core)
    (let [query "
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX wd: <http://www.wikidata.org/entity/>
    SELECT ?enLabel
    WHERE
    {
      wd:Q5 rdfs:label ?enLabel.
      Filter (Lang(?enLabel) = \"en\")
    }"
      ]
      (map simplify (sparql-select wikidata-endpoint query))
    
    ;; => ({:enLabel "human"})
    ;; Compare to [{"enLabel" {"xml:lang" "en", "type" "literal", "value" "human"}}]


####  Optional `translators` argument

`simplify` takes an optional argument `translators`, a map with three
keys: `:uri`, `:lang` and `:datatype`. Default values for this map are
defined as the value `default-translators`.
    
| key | description | default  |
| --- |--- | ---|
| `:uri` | value is a uri| return raw value (typically "http://...") |
| `:lang` | value is literal and has a language tag, e.g. "en" | return raw value (without the language tag)|
| `:datatype` | value is literal and has an assigned datatype, g.g. "xsd:int" | parse XSD values, otherwise return raw value |
| `:bnode` | value is a blank node | return raw value, typically like "b0" |
    
By default the Jena library is referenced to translate [xsd datatypes](https://www.w3.org/TR/xmlschema11-2/) into instances of an appropriate class. In the following example, Obama's date of birth is translated to an instance of Jena's `XSDDateTime`, which has a `getYears` method:
    
        (use 'ont-app.sparql-endpoint.core)
        (let [query "
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX wd: <http://www.wikidata.org/entity/>
        # What is Obama's date of birth?
        SELECT ?dob
        WHERE 
        {
          wd:Q76 wdt:P569 ?dob.
        } "
          ]
          (.getYears (:dob (nth (map simplify 
                                     (sparql-select wikidata-endpoint query))
                                 0))))
        ;; -> 1961
    
Any of these values can be overridden with custom functions by merging `default-translators` with an overriding map.


### simplifier-for-prologue

This function takes a query with a prologue (Including a set of PREFIX
declarations) and returns a simplifier function informed by a function
which maps full URIs to their corresponding quicknames. It is informed
by the function **parse-prologue**, described below.

Compare this&#x2026;

    (use 'ont-app.sparql-endpoint.core)
    (let [query "
    # Things called 'Barack Obama'
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX wd: <http://www.wikidata.org/entity/>
    SELECT *
    WHERE
    {
      ?Q rdfs:label \"Barack Obama\"@en.
    }"
      ]
      (map simplify
           (sparql-select wikidata-endpoint query)))
    ;; -> ({:Q "http://www.wikidata.org/entity/Q76"} 
    ;;     {:Q "http://www.wikidata.org/entity/Q47513588"}) 

&#x2026; to this &#x2026;

    (use 'ont-app.sparql-endpoint.core)
    (let [query "
    # Things called 'Barack Obama'
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX wd: <http://www.wikidata.org/entity/>
    SELECT *
    WHERE
    {
      ?Q rdfs:label \"Barack Obama\"@en.
    }"
      ]
      (map (simplifier-for-prologue query)
           (sparql-select wikidata-endpoint query)))
    
    ;; => ({:Q "wd:Q76"} {:Q "wd:Q47513588"}) 


## parse-prologue

This function takes a SPARQL query and returns a vector with three values:
`base`, `uri-to-quickname`, `quickname-to-uri`. 


| name | description |
| --- | --- |
| base | The base URI used to resolve relative URIs |
| uri-to-quickname | fn[uri] -> corresponding quickname |
| quickname-to-uri | fn[quickname] -> corresponding full URI |


Given a string for which there is no prefix declaration in the query,
these last two functions will return their argument unchanged.


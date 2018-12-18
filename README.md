

# Introduction

sparql-endpoint provides utilities for interfacing with [SPARQL 1.1](https://www.w3.org/TR/sparql11-query/)
endpoints in clojure.



# Installation

`sparql-endpoint` is available as a Maven artifact from clojars. 

[![Clojars Project](https://img.shields.io/clojars/v/ont-app/sparql-endpoint.svg)](https://clojars.org/ont-app/sparql-endpoint)


# Functions


## Functions that interact with SPARQL endpoints

These involve POSTs to an [update endpoint](https://www.w3.org/TR/sparql11-update/) and GETs to a [query
enpoint](https://www.w3.org/TR/sparql11-query/). There are special functions for ASK, SELECT and CONSTRUCT
queries. Each of these take mandatory <span class="underline">**endpoint**</span> and <span class="underline">**query**</span>
arguments, and an optional <span class="underline">**http-req**</span> argument.



### Mandatory arguments: <span class="underline">endpoint</span> and <span class="underline">query</span>

All of the basic query and update functions take two mandatory arguments: 

<span class="underline">**endpoint**</span> is the URL of a SPARQL endpoint

<span class="underline">**query**</span> is a string in an appropriate format for ASK, SELECT,
CONSTRUCT, or one of the UPDATE operations.



### Optional argument: <span class="underline">http-req</span>

HTTP calls are done through [clj-http](https://github.com/dakrone/clj-http). There is a third optional
<span class="underline">**http-req**</span> argument which may include additional HTTP request
parameters.

For example if <span class="underline">endpoint</span> requires authentication, you may specify
<span class="underline">{:basic-auth "myUserName:myPassword"}</span>

{:cookie-policy :standard} is asserted by default, but this can
be overridden. The <span class="underline">:query-params</span> parameter is reserved, as it is
needed to specify the query to the endpoint.


### sparql-ask

This function takes an endpoint and a SPARQL ASK query and returns a boolean:

    (use 'sparql-endpoint.core)
    (sparql-ask 
        "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
        "ASK WHERE {wd:Q5 rdfs:label \"human\"@en}")
    ;; --> true

### sparql-select

This function takes as its <span class="underline">query</span> parameter a SPARQL SELECT query:

    (use 'sparql-endpoint.core)
    (let [query "
    # What is the English name for Q5?
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
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
the endpoint. These can be mapped by more expressive <span class="underline">**simplifiers**</span>,
described below.


### sparql-construct

This function takes a SPARQL CONSTRUCT query as its query parameter
and returns a string of [turtle](https://www.w3.org/TR/turtle/) describing the results.

    (use 'sparql-endpoint.core)
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

By default the output of <span class="underline">**sparql-select**</span> is parsed JSON of raw
output of the endpoint, using [the specification described by W3C](https://www.w3.org/TR/sparql11-results-json/). 

    {'value' <value>
     'type' 'uri' | 'literal'
     ;;...maybe...
     'xml:lang' <lang> (if literal)
     'datatype' <datatype> (if literal)
    }

It is usually convenient to transform these bindings into simpler
representations. Hence the functions <span class="underline">**simplify**</span> and
<span class="underline">**simplifier-for-prologue**</span>, described below.


### simplify

The function <span class="underline">simplify</span> will take a result binding and return a simplified map:

    (use 'sparql-endpoint.core)
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

1.  Optional <span class="underline">**translators**</span> argument

    <span class="underline">simplify</span> takes an optional argument <span class="underline">**translators**</span>, a map with three
    keys: <span class="underline">**:uri**</span>, <span class="underline">**:lang**</span> and <span class="underline">**:datatype**</span>. Default values for this map are
    defined as the value **default-translators**.
    
    <table border="2" cellspacing="0" cellpadding="6" rules="groups" frame="hsides">
    
    
    <colgroup>
    <col  class="org-left" />
    
    <col  class="org-left" />
    
    <col  class="org-left" />
    </colgroup>
    <thead>
    <tr>
    <th scope="col" class="org-left">key</th>
    <th scope="col" class="org-left">description</th>
    <th scope="col" class="org-left">default</th>
    </tr>
    </thead>
    
    <tbody>
    <tr>
    <td class="org-left">:uri</td>
    <td class="org-left">value is a URI</td>
    <td class="org-left">return raw value</td>
    </tr>
    
    
    <tr>
    <td class="org-left">:lang</td>
    <td class="org-left">value is literal and has a language tag, e.g. "en"</td>
    <td class="org-left">return raw value</td>
    </tr>
    
    
    <tr>
    <td class="org-left">:datatype</td>
    <td class="org-left">value is literal and has an assigned datatype, e.g. "xsd:int"</td>
    <td class="org-left">parse XSD values, otherwise return raw value</td>
    </tr>
    </tbody>
    </table>
    
    By default the Jena library is referenced to translate [xsd datatypes](https://www.w3.org/TR/xmlschema11-2/)
    into instances of an appropriate class. In the following example,
    Obama's date of birth is translated to an instance of Jena's
    **XSDDateTime**, which has a <span class="underline">**getYears**</span> method&#x2026;
    
        (use 'sparql-endpoint.core)
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
    
    Any of these values can be overridden with custom functions by
    merging **default-translators** with an overriding map.


### simplifier-for-prologue

This function takes a query with a prologue (Including a set of PREFIX
declarations) and returns a simplifier function informed by a function
which maps full URIs to their corresponding quicknames. It is informed
by the function **parse-prologue**, described below.

Compare this&#x2026;

    (use 'sparql-endpoint.core)
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

    (use 'sparql-endpoint.core)
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
<span class="underline">**base**</span>, <span class="underline">**uri-to-quickname**</span>, <span class="underline">**quickname-to-uri**</span>. 

<table border="2" cellspacing="0" cellpadding="6" rules="groups" frame="hsides">


<colgroup>
<col  class="org-left" />

<col  class="org-left" />
</colgroup>
<thead>
<tr>
<th scope="col" class="org-left">name</th>
<th scope="col" class="org-left">description</th>
</tr>
</thead>

<tbody>
<tr>
<td class="org-left">base</td>
<td class="org-left">The base URI used to resolve relative URIs</td>
</tr>


<tr>
<td class="org-left">uri-to-quickname</td>
<td class="org-left">fn[uri] -> corresponding quickname</td>
</tr>


<tr>
<td class="org-left">quickname-to-uri</td>
<td class="org-left">fn[quickname] -> corresponding full URI</td>
</tr>
</tbody>
</table>

Given a string for which there is no prefix declaration in the query,
these last two functions will return their argument unchanged.


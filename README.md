#<img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="NaturalLexicon logo" :width=100 height=100/> ont-app/sparql-endpoint

The `sparql-endpoint` library provides utilities for interfacing with
[SPARQL 1.1](https://www.w3.org/TR/sparql11-query/) endpoints in
clojure.

# Contents
- [Installation](#h1-installation)
- [Functions](#h1-functions)
  - [Functions that interact with SPARQL endpoints](#h2-functions-that-interact-with-sparql-endpoints)
    - [Mandatory arguments: `endpoint` and `query`](#h3-mandatory-arguments)
<a name="h1-introduction"></a>
    - [Optional argument: `http-req`](#h3-optional-argument-http-req)
    - [sparql-ask](#h3-sparql-ask)
    - [sparql-select](#h3-sparql-select)
    - [sparql-construct](#h3-sparql-construct)
    - [sparql-update](#h3-sparql-update)
  - [Simplifiers](#h2-simplifiers)
    - [simplify](#h3-simplify)
      - [Optional `translators` argument](#h4-optional-translators-argument)
    - [Customizing simplifiers](#h3-customizing-simplifiers)
    - [LangStr](#h3-langstr)
    - [meta-tagged-literal](#h3-meta-tagged-literal)
    - [simplifier-for-prolog](#h3-simplifier-for-prologue)
  - [parse-prologue](#h2-parse-prologue)
  - [`xsd-type-uri`](#h2-xsd-type-uri)
  - [License](#h2-license)


<a name="h1-installation"></a>
# Installation

`sparql-endpoint` is available as a Maven artifact from clojars. 

[![Clojars Project](https://img.shields.io/clojars/v/ont-app/sparql-endpoint.svg)](https://clojars.org/ont-app/sparql-endpoint)


Additional documentation is provided at https://cljdoc.org/d/ont-app/sparql-endpoint/0.1.2.

Require thus:
```
(ns my.namespace
  (:require
    [ont-app.sparql-endpoint.core :as endpoint :refer :all]
    ))
```

<a name="h1-functions"></a>
# Functions

<a name="h2-functions-that-interact-with-sparql-endpoints"></a>
## Functions that interact with SPARQL endpoints

Interacting with a SPARQL endpoint involves POSTs to an [update
endpoint](https://www.w3.org/TR/sparql11-update/) and GETs to a [query
endpoint](https://www.w3.org/TR/sparql11-query/). There are special
functions for ASK, SELECT and CONSTRUCT queries. Each of these take
mandatory `endpoint` and `query` arguments, plus an optional `http-req`
map.


<a name="h3-mandatory-arguments"></a>
### Mandatory arguments: `endpoint` and `query`

All of the basic query and update functions take two mandatory arguments: 

`endpoint` is the URL of a SPARQL endpoint

`query` is a string in an appropriate format for ASK, SELECT,
CONSTRUCT, or one of the UPDATE operations.


<a name="h3-optional-argument-http-req"></a>
### Optional argument: `http-req`

HTTP calls are done through the
[clj-http](https://github.com/dakrone/clj-http) library. There is a
third optional `http-req` argument which may include additional HTTP
request parameters. 

For example if `endpoint`requires authentication, you may specify
`{:basic-auth "myUserName:myPassword"}`

`[{:cookie-policy
:standard`}](https://github.com/dakrone/clj-http#get) is asserted by
default, but this can be overridden. The `:query-params` parameter is
reserved, as it is needed to specify the query to the endpoint.

<a name="h3-sparql-ask"><a/>
### sparql-ask

This function takes an endpoint and a [SPARQL
ASK](https://www.w3.org/TR/rdf-sparql-query/#ask) query and returns a
boolean:
```
> (sparql-ask 
    "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
    "ASK WHERE {wd:Q5 rdfs:label \"human\"@en}")
true
>
```
<a name="h3-sparql-select"></a>
### sparql-select

This function takes as its `query` parameter a [SPARQL
SELECT](https://www.w3.org/TR/rdf-sparql-query/#select) query:

```
> (let [query "# What is the English name for Q5?
               PREFIX rdfs: `
               PREFIX wd: <http://www.wikidata.org/entity/>
               SELECT ?enLabel
               WHERE
               {
                  wd:Q5 rdfs:label ?enLabel.
                  Filter (Lang(?enLabel) = \"en\")
               }
              "
       ]
       (sparql-select 
          "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
          query))
          
[{"enLabel" {"xml:lang" "en", "type" "literal", "value" "human"}}]
>
```

The bindings returned are direct translations of the JSON returned by
the endpoint. These can be mapped by more expressive `simplifiers`,
described [below](#h2-simplifiers).

<a name="h3-sparql-construct"></a>
### sparql-construct

This function takes a [SPARQL
CONSTRUCT](https://www.w3.org/TR/sparql11-query/#construct) query as
its query parameter and returns a string of
[turtle](https://www.w3.org/TR/turtle/) describing the results.

```
> (let [query "# Things called 'human'
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
               }
              "
       ]
       (sparql-construct       
          "https://query.wikidata.org/bigdata/namespace/wdq/sparql"
           query))
   
"
@prefix eg: <http://example.com/> .
@prefix wd: <http://www.wikidata.org/entity/> .
wd:Q823310 a eg:Human .
wd:Q20094897 a eg:Human .
wd:Q26190966 a eg:Human .
wd:Q5 a eg:Human .
"
>
```

<a name="h3-sparql-update"></a>
### sparql-update

This function POSTS its query parameter (CREATE, INSERT, DELETE, etc)
to the specified [SPARQL
update](https://www.w3.org/TR/sparql11-update/) endpoint, and returns
the plain text response.

```
> (sparql-update "http://localhost:3030/my-dataset/update"
                 "DROP GRAPH <http://myGraph>")
<html>
<head>
</head>
<body>
<h1>Success</h1>
<p>
Update succeeded
</p>
</body>
</html>
> 
```

<a name="h2-simplifiers"></a>
## Simplifiers

By default the output of `sparql-select` is parsed from raw JSON output
of the endpoint, using [the specification described by
W3C](https://www.w3.org/TR/sparql11-results-json/).

```
{'value' <value>
 'type' 'uri' | 'literal'
 ;;...maybe...
 'xml:lang' <lang> (if literal)
 'datatype' <datatype> (if literal)
}
```

It is usually convenient to transform these bindings into simpler
representations. Hence the functions [simplify](#h3-simplify) and
[simplifier-for-prologue](#h3-simplifier-for-prologue), described
below.

<a name="h3-simplify"></a>
### simplify

The function
[simplify](https://cljdoc.org/d/ont-app/sparql-endpoint/0.1.2/api/ont-app.sparql-endpoint.core#simplify)
will take a result binding and return a simplified map `{<var>
<value>...}`. This would typically be done in the context of a map
function:

```
(def human-query "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             PREFIX wd: <http://www.wikidata.org/entity/>
             SELECT ?enLabel
             WHERE
             {
               wd:Q5 rdfs:label ?enLabel.
               Filter (Lang(?enLabel) = \"en\")
             }")
  (map simplify (sparql-select wikidata-endpoint human-query))
({:enLabel #lstr "human@en"})
;; Compare to [{"enLabel" {"xml:lang" "en", "type" "literal", "value" "human"}}]
>
```

The `#lstr` reader macro is defined for literal values with
`xml:lang` tags. Described in more detail in the next sections.

<a name="h4-optional-translators-argument"></a>
####  Optional `translators` argument

`simplify` optionally takes two arguments, the first of which is
`translators`, a map with four keys: `:uri`, `:lang`, `:datatype` and
`:bnode`. Default values for this map are defined as the value
`default-translators`.

Each key maps to a function of the form `[sparql-binding] -> value`
    
| key | description | default  |
| --- | --- | --- |
| `:uri` | value is a uri| return raw URI string (typically "http://...") |
| `:lang` | value is literal and has a language tag, e.g. "en" | return a [LangStr](#h5-LangStr) |
| `:datatype` | value is literal and has an assigned datatype, g.g. "xsd:int" | parse XSD values, otherwise return a [meta-tagged-literal](#h5-meta-tagged-literal) |
| `:bnode` | value is a blank node | return raw value, typically like "b0" |
    
By default the Jena library is referenced to translate [xsd
datatypes](https://www.w3.org/TR/xmlschema11-2/) into instances of an
appropriate class. In the following example, Obama's date of birth is
translated to an instance of Jena's
[XSDDateTime](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/datatypes/xsd/XSDDateTime.html),
which has a
[getYears](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/datatypes/xsd/XSDDateTime.html#getYears--)
method:
    
```
> (let [query "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               PREFIX wd: <http://www.wikidata.org/entity/>
               # What is Obama's date of birth?
               SELECT ?dob
               WHERE 
               {
                 wd:Q76 wdt:P569 ?dob.
               } "
       ]
       (-> (sparql-select wikidata-endpoint query)
           (partial (map simplify))
           (first)
           (:dob)
           (.getYears)))
1961
>
```
<a name="h3-customizing-simplifers"></a>
### Customizing simplifiers

Any of these values can be overridden with custom functions by merging
`default-translators` with an overriding map.


Since `ont-app/vocabulary` is a dependency of this library, there is an alternative to the default simplifier called `simplifier-with-kwis`

```
> (use 'ont-app.vocabulary.wikidata)
> (->> 
    (sparql/sparql-select wikidata-endpoint human-query)
    (map sparql/simplifier-with-kwis)
    (set))
#{{:q :wd/Q823310} {:q :wd/Q5} {:q :wd/Q73755406}}
> (voc/uri-for :wd/Q5)
"http://www.wikidata.org/entity/Q5"
```

This was defined using the functions `update-translators` and `make-simplifier`:

```
(def simplifier-with-kwis
  (make-simplifier (update-translators default-translators
                                       :uri voc/keyword-for)))
```

<a name="h3-LangStr"></a>
### LangStr

LangStr is a type which holds a string and a language tag.

It is defined in the supporting library `ont-app.vocabulary.lstr`.

Examples:

```clojure
> (type #lstr "human@en")
ont_app.vocabulary.lstr.LangStr
> (str #lstr "human@en")
human
> (ont-app.vocabulary.lstr/lang  #lstr "human@en")
"en"
```

See the docs for [ont-app/vocabulary](https://github.com/ont-app/vocabulary#h2-language-tagged-strings) for details.


<a name="h3-meta-tagged-literal"></a>
### meta-tagged-literal

The function `meta-tagged-literal` reifies an Object whose _toString_
method is the "value" field in the SPARQL binding map, and whose
metadata contains everything else.

This is used In the case of non-xsd datatypes (typically encoded in
SPARQL as '"<datatype-encoded-with-my-syntax>"^^MyDatatype'):

```
> (def my-datum 
   (meta-tagged-literal 
     {"datatype" "MyDatatype" 
      "type" "literal" 
      "value" "<datatype-encoded-with-my-syntax>"}))
my-datum
> 
> my-datum
#object[ont_app.sparql_endpoint.core$meta_tagged_literal$reify__14016 0x2a92b8aa "<datatype-encoded-with-my-syntax>"]
>
> (str my-datum)
"<datatype-encoded-with-my-syntax>"
>
> (meta my-datum)
{"type" "literal", "datatype" "MyDatatype"}
>
```


<a name="h3-simplifier-for-prologue"></a>
### simplifier-for-prologue

This function takes a query with a prologue (Including a set of PREFIX
declarations) and returns a simplifier function informed by a function
which maps full URIs to their corresponding quicknames. It is informed
by the function **parse-prologue**, described below.

Compare this&#x2026;

```
(let [query "# Things called 'Barack Obama'
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
({:Q "http://www.wikidata.org/entity/Q76"} 
 {:Q "http://www.wikidata.org/entity/Q47513588"}) 
>
```

&#x2026; to this &#x2026;

```
(let [query "# Things called 'Barack Obama'
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
    
({:Q "wd:Q76"} {:Q "wd:Q47513588"}) 
>
```

<a name="h2-parse-prologue"></a>
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

```
> (let [query "BASE <http://example.org/>
               PREFIX eg: <http://example.com/> 
               Select * where {?s ?p ?o.}"
        [base, u-to-q, q-to-u]  (sparql/parse-prologue query)
        ]
  [base,
   (u-to-q "<http://example.com/blah>")
   (q-to-u  "eg:blah")
   (u-to-q "blah")
   (q-to-u "blah")
   ])
   
["http://example.org/"
 "eg:blah"
 "http://example.com/blah"
 "blah"
 "blah"
 ]
>
```


<a name="h2-xsd-type-uri"></a>
## `xsd-type-uri`

It is possible to get the URI string for xsd types using the same Jena
library that parses them. This works for most of the standard types:

```
> (sparql/xsd-type-uri 1)
"http://www.w3.org/2001/XMLSchema#long"
>
> (sparql/xsd-type-uri 1.0)
"http://www.w3.org/2001/XMLSchema#double"
>
> (sparql/xsd-type-uri #inst "2020-02-14")
"http://www.w3.org/2001/XMLSchema#dateTime"
>
```

<a name="h2-license"></a>
## License

Copyright © 2020-1 Eric D. Scott

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

<table>
<tr>
<td width=75>
<img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="Natural Lexicon logo" :width=50 height=50/> </td>
<td>
<p>Natural Lexicon logo - Copyright © 2020 Eric D. Scott. Artwork by Athena M. Scott.</p>
<p>Released under <a href="https://creativecommons.org/licenses/by-sa/4.0/">Creative Commons Attribution-ShareAlike 4.0 International license</a>. Under the terms of this license, if you display this logo or derivates thereof, you must include an attribution to the original source, with a link to https://github.com/ont-app, or  http://ericdscott.com. </p> 
</td>
</tr>
<table>

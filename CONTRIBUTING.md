# Contributing

## The `master` and `develop` branches

Each repo in ont-app which is not 'under contruction' has two branches:

- _master_, which reflects the state of the _current-release_
- _develop_, which reflects the state of the _next-release_

The master branch should only be modified in ways that do not change
the behavior of the current release, mainly clarification of
documentation. Changes to documenation on the master branch should
apply only to the current release.

Any future development should work off of the develop branch.  I will
endeavor to synchronize the develop branch with a clojars SNAPSHOT of
the next version (as indicated in _project.clj_). Features of this
snapshot may or may not be comprehensively tested, or documented
accurately, but they should 'mostly work'.

## Pull requests

This is a good tutorial for submitting PRs:

https://kbroman.org/github_tutorial/pages/fork.html

To avoid wasted duplication of effort, please make sure there is an
issue dedicated to the change you are looking to implement, and
use the issue's messaging utility to discuss it in advance. 

The preferred sequence of events would be:
- A draft of documentation relating to the change (in the issue)
- Sketches of the pertinent test(s) (in the issue)
- The actual PR, which would include finalized versions of the two
  points above.
  
## Testing

The `ont-app.sparql-endpoint.core-test/test-updates` test is
predicated on setting an environment variable
`ONT_APP_TEST_UPDATE_ENDPOINT`, which should point to a SPARQL
endpoint with both query and update permissions and end with a slash.

EG:

```
ONT_APP_TEST_UPDATE_ENDPOINT=http://localhost:3030/test-dataset/ make test-all
```

The test will be skipped if this variable is not set.

## Code of conduct

Be nice.

To clarify, please follow [these
guidelines](https://www.contributor-covenant.org/version/2/0/code_of_conduct/).

For complaints, please contact Eric Scott at {first-name}.{last-name}@acm.org.



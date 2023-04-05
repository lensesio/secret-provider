![Action Status](https://github.com/lensesio/secret-provider/workflows/CI/badge.svg)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Flensesio%2Fsecret-provider.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Flensesio%2Fsecret-provider?ref=badge_shield)
[<img src="https://img.shields.io/badge/docs--orange.svg?"/>](https://docs.lenses.io/4.0/integrations/connectors/secret-providers/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Secret Provider

Secret provider for Kafka to provide indirect look up of configuration values.

## Description

External secret providers allow for indirect references to be placed in an
applications' configuration, so for example, that secrets are not exposed in the
Worker API endpoints of Kafka Connect.

For [Documentation](https://docs.lenses.io/4.0/integrations/connectors/secret-providers/).


## Contributing

We'd love to accept your contributions! Please use GitHub pull requests: fork
the repo, develop and test your code,
[semantically commit](http://karma-runner.github.io/1.0/dev/git-commit-msg.html)
and submit a pull request. Thanks!

### Building

***Requires SBT to build.***

To build the (scala 2.12 and 2.13) assemblies for use with Kafka Connect (also runs tests):

```bash
sbt +assembly
```

To run tests:

```bash
sbt +test
```


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Flensesio%2Fsecret-provider.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Flensesio%2Fsecret-provider?ref=badge_large)
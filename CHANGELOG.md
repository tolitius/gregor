# Change Log
All notable changes to Gregor will be documented in this file.

## [0.5.1] - 2016-08-28

### Fixed

- Fix rack-aware-mode-constant by replacing class with object instance.

## [0.5.0] - 2016-08-23

### Added
- new function `topics` which lists existing Kafka topics.

### Changes
- upgrade to Kafka `0.10.0.1`
- `kafka.admin.AdminUtils/createTopic` acquired another argument in `0.10`. It's an
  additional optional argument for `create-topic`.

### Fixed
- fix producer doc link
- remove unused Java class import

## [0.4.1] - 2016-07-10

### Added
- Closeable protocol for consumers and producers via
  [Pull Request #9](https://github.com/weftio/gregor/pull/9) which includes closing a
  producer with a timeout in seconds.

### Changes
- Use Kafka 0.9 compiled with Scala 2.11 instead of 2.10 because the Kafka maintainers
  recommend to do so.

## [0.4.0] - 2016-06-08

### Added
Topic management via [Pull Request #5](https://github.com/weftio/gregor/pull/5):

- `create-topic`: Create a new topic.
- `topic-exists?`: Check if a topic exists.
- `delete-topic`: Delete an existing topic.

### Fixed
- [Pull Request #7](https://github.com/weftio/gregor/pull/7): Avoid a
  `NullPointerException` in the rebalance listener.
- [Pull Request #4](https://github.com/weftio/gregor/pull/4): Exclude
  `clojure.core/flush` and `clojure.core/send`.

## [0.3.1] - 2016-05-18

### Added
- vars for byte-array (de)serializers

## [0.3.0] - 2016-05-09

Apologies for the several (pretty minor) breaking changes.

### Breaking Changes
- Arity of `resume` and `pause` now aligns with the rest of the API (`assoc`-like
  optional arg pairs)
- `commit-offsets-async!` and `commit-offsets!` optional arg `offsets` is now a seq of
  maps with `:topic`, `:partition`, `:offset` and optional `:metadata` keys.
- `commited` now returns `nil` or a map with `:offset` and `:metadata` keys.
- `send` no longer supports a callback, use `send-then` instead.

### Changes
- Second `seek-to!` arg is now named `offset`.
- `send` has new arities that correspond to those of the `ProducerRecord` constructor.

### Fixed
- `resume` and `pause` no longer have the same implementation.
- Merge pull request from `lambdahands` which fixes issue w/ overwritten custom
  (de)serializers in producer and consumer configs.

### Added
- `send-then` function which provides `send` a callback. This callback expects a map of
  metadata and an exception as its args.
- `->producer-record` function.


## [0.2.0] - 2016-03-25

### Added
- First public release: added most of API

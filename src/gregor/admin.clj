(ns gregor.admin
  (:refer-clojure :exclude [flush send])
  (:import [kafka.admin AdminUtils]
           [kafka.utils ZkUtils]
           [scala.collection JavaConversions])
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; TODO: ZkUtils is depricated and removed in the current version of kafka: https://github.com/apache/kafka/pull/5480
;;       hence this needs to be refactored with KafkaZkClient to work

;;;;;;;;;;;;;;;;;;;;;;
;; Topic Management ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- validate-zookeeper-config
  "A helper for validating a Zookeeper configuration map and applying default values. Any
   invalid item in the provided config will result in an assertion failure.

   Args:
    - `config-map`: a map with Zookeeper connection details. Valid keys are as follows:

        `:connection-string` a valid connection string for the Zookeeper instance to connect to.
        `:session-timeout` (optional) the session timeout in millis.
        `:connect-timeout` (optional) the connect timeout in millis."
  [config-map]
  (let [defaults {:connection-string nil
                  :session-timeout 10000
                  :connect-timeout 10000}
        merged (merge defaults config-map)
        valid-keys (set (keys defaults))
        merged-keys (set (keys merged))]
    (assert (set/subset? merged-keys valid-keys)
            (format "Invalid Zookeeper config: %s"
                    (str/join ", " (set/difference merged-keys valid-keys))))
    (assert (string? (:connection-string merged))
            "Zookeeper config must contain a valid :connection-string")
    (assert (integer? (:session-timeout merged))
            "Zookeeper :session-timeout must be an integer")
    (assert (integer? (:connect-timeout merged))
            "Zookeeper :connect-timeout must be an integer")
    merged))


(defmacro with-zookeeper
  "A utility macro for interacting with Zookeeper.

   Args:
    - `zk-config`: a map with Zookeeper connection details. This will be validated using
                   `validate-zookeeper-config` before use.
    - `zookeeper`: this will be bound to an instance of `ZkUtils` while the body is executed.
                   The connection to Zookeeper will be cleaned up when the body exits."
  [zk-config zookeeper & body]
  `(let [zk-config# (validate-zookeeper-config ~zk-config)
         client-and-conn# (ZkUtils/createZkClientAndConnection
                           (:connection-string zk-config#)
                           (:session-timeout zk-config#)
                           (:connect-timeout zk-config#))]
     (with-open [client# (._1 client-and-conn#)
                 connection# (._2 client-and-conn#)]
       (let [~zookeeper (ZkUtils. client# connection# false)]
         ~@body))))


(def rack-aware-modes
  {:disabled (kafka.admin.RackAwareMode$Disabled$.)
   :enforced (kafka.admin.RackAwareMode$Enforced$.)
   :safe     (kafka.admin.RackAwareMode$Safe$.)})


(defn- rack-aware-mode-constant
  "Convert a keyword name for a `RackAwareMode` into the appropriate constant from the
   underlying Kafka library.

   Args:
    - `mode`: a keyword of the same name as one of the constants in `kafka.admin.RackAwareMode`."
  [mode]
  (when-not (contains? rack-aware-modes mode)
    (throw (IllegalArgumentException. (format "Bad RackAwareMode: %s" mode))))
  (get rack-aware-modes mode))


(defn create-topic
  "Create a topic.

   Args:
    - `zk-config`: a map with Zookeeper connection details as expected by `with-zookeeper`.
    - `topic`: the name of the topic to create.
    - an unnamed configuration map. Valid keys are as follows:

      `:partitions`         (optional) The number of ways to partition the topic. Defaults to 1.
      `:replication-factor` (optional) The replication factor for the topic. Defaults to 1.
      `:config`             (optional) A map of configuration options for the topic.
      `:rack-aware-mode`    (optional) Control how rack aware replica assignment is done.
                                       Valid values are `:disabled`, `:enforced`, `:safe`.
                                       Default is `:safe`."
  [zk-config topic {:keys [partitions replication-factor config rack-aware-mode]
                    :or {partitions 1
                         replication-factor 1
                         config nil
                         rack-aware-mode :safe}}]
  (with-zookeeper zk-config zookeeper
    (AdminUtils/createTopic zookeeper
                            topic
                            (int partitions)
                            (int replication-factor)
                            (as-properties config)
                            (rack-aware-mode-constant rack-aware-mode))))


(defn topics
  "Query existing topics.

   Args:
    - `zk-config`: a map with Zookeeper connection details as expected by `with-zookeeper`."
  [zk-config]
  (with-zookeeper zk-config zookeeper
    (-> zookeeper .getAllTopics JavaConversions/seqAsJavaList seq)))


(defn topic-exists?
  "Query whether or not a topic exists.

   Args:
    - `zk-config`: a map with Zookeeper connection details as expected `with-zookeeper`.
    - `topic`: The name of the topic to check for."
  [zk-config topic]
  (with-zookeeper zk-config zookeeper
    (AdminUtils/topicExists zookeeper topic)))


(defn delete-topic
  "Delete a topic.

   Args:
    - `zk-config`: A map with Zookeeper connection details as expected by `with-zookeeper`.
    - `topic`: The name of the topic to delete."
  [zk-config topic]
  (with-zookeeper zk-config zookeeper
    (AdminUtils/deleteTopic zookeeper topic)))

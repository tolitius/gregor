(ns gregor.core-test
  (:require [clojure.test :refer [deftest is]]
            [gregor.core :refer :all])
  (:import java.util.concurrent.TimeUnit
           [org.apache.kafka.clients.consumer ConsumerRecord MockConsumer OffsetResetStrategy]
           org.apache.kafka.clients.producer.MockProducer
           org.apache.kafka.common.serialization.StringSerializer))

(deftest producing
  (let [p (MockProducer. true (StringSerializer.) (StringSerializer.))]
    (send p "unittest" {:a 1 :b "foo"})
    (send-then p "unittest" {:a 2 :b "bar"} (fn [metadata ex]))
    (let [values (.history p)
          one    (-> values first .value)
          two    (-> values second .value)]
      (is (= {:a 1 :b "foo"} one))
      (is (= {:a 2 :b "bar"} two))
      (.close p))))

(deftest send-arities
  (let [time      (System/currentTimeMillis)
        partition 0
        topic     "unittest"
        p         (MockProducer. true (StringSerializer.) (StringSerializer.))
        key       0]

    (send p topic 1)
    (send-then p topic 2 (fn [_ _]))
    (is (= 1 (-> p .history first .value)))
    (is (= 2 (-> p .history second .value)))

    (send p topic key 3)
    (send-then p topic key 4 (fn [_ _]))
    (is (= 3 (-> p .history (#(nth % 2)) .value)))
    (is (= 4 (-> p .history (#(nth % 3)) .value)))

    (send p topic partition key 5)
    (send-then p topic partition key 6 (fn [_ _]))
    (is (= 5 (-> p .history (#(nth % 4)) .value)))
    (is (= 6 (-> p .history (#(nth % 5)) .value)))

    (send p topic partition time key 7)
    (send-then p topic partition time key 8 (fn [_ _]))
    (is (= 7 (-> p .history (#(nth % 6)) .value)))
    (is (= 8 (-> p .history (#(nth % 7)) .value)))

    (.close p)))

(deftest subscribing
  (let [c (consumer "localhost:9092" "unit-test" ["test-topic"])]
    (is (= #{"test-topic"} (subscription c)))
    (unsubscribe c)
    (is (= #{} (subscription c)))
    (close c)))

(deftest consuming
  (let [c  (MockConsumer. (OffsetResetStrategy/EARLIEST))
        _  (assign! c "test-topic" 0)
        c  (doto c
             (.updateBeginningOffsets {(topic-partition "test-topic" 0) 0})
             (.addRecord (ConsumerRecord. "test-topic" 0 0 0 {:a 1}))
             (.addRecord (ConsumerRecord. "test-topic" 0 1 0 {:b 2}))
             ;; duplicate offset (uniquely identifies each record within the partition)
             (.addRecord (ConsumerRecord. "test-topic" 0 0 0 {:c 3}))
             (.addRecord (ConsumerRecord. "test-topic" 0 2 0 {:d 4})))
        ms (records c)]
    (is (= [{:a 1} {:b 2} {:d 4}]
           (mapv :value (first ms))))
    (is (= #{(topic-partition "test-topic" 0)}
           (assignment c)))
    (.close c)))

(deftest commit
  (let [c (doto (MockConsumer. (OffsetResetStrategy/EARLIEST))
            (assign! "unittest" 0)
            (.updateBeginningOffsets {(topic-partition "unittest" 0) 0})
            (.addRecord (ConsumerRecord. "unittest" 0 1 0 {:key :a})))]
    (is (= nil (committed c "unittest" 0)))
    (poll c)
    (commit-offsets! c)
    (is (= {:offset 2 :metadata nil} (committed c "unittest" 0)))
    (.addRecord c (ConsumerRecord. "unittest" 0 2 0 {:key :b}))
    (poll c)
    (commit-offsets-async! c)
    (is (= {:offset 3 :metadata nil} (committed c "unittest" 0)))
    (.addRecord c (ConsumerRecord. "unittest" 0 3 0 {:key :c}))
    (poll c)
    (commit-offsets-async! c (fn [om ex]))
    (is (= {:offset 4 :metadata nil} (committed c "unittest" 0)))
    (.addRecord c (ConsumerRecord. "unittest" 0 4 0 {:key :c}))
    (poll c)
    (is (= 5 (position c "unittest" 0)))
    (commit-offsets-async! c [{:topic "unittest" :partition 0 :offset 5}] (fn [om ex]))
    (is (= {:offset 5 :metadata nil} (committed c "unittest" 0)))
    (seek! c "unittest" 0 2)
    (is (= 2 (position c "unittest" 0)))
    (seek-to! c :beginning "unittest" 0)
    (is (= 0 (position c "unittest" 0)))
    (is (thrown? IllegalStateException
                 (seek-to! c :end "unittest" 0)
                 (= 5 (position c "unittest" 0))))))

(defn- zookeeper-config
  []
  (when-let [connection-string (System/getenv "GREGOR_TEST_ZOOKEEPER")]
    {:connection-string connection-string
     :session-timeout 10000
     :connect-timeout 10000}))

(defn- wait-for
  [pred timeout-ms]
  (let [start (System/currentTimeMillis)]
    (while (and
            (not (pred))
            (< (System/currentTimeMillis) (+ start timeout-ms)))
      (Thread/sleep 100))))

(deftest topic-management
  (when-let [zk-config (zookeeper-config)]
    (let [topic "test-topic"]
      (is (not (topic-exists? zk-config topic)))

      (create-topic zk-config topic {})
      (wait-for #(topic-exists? zk-config topic) 10000)
      (is (topic-exists? zk-config topic))

      (is (not (empty? (topics zk-config))))
      (is (some #{topic} (topics zk-config)))

      (delete-topic zk-config topic)
      (wait-for #(not (topic-exists? zk-config topic)) 10000)
      (is (not (topic-exists? zk-config topic))))))

(extend-protocol Closeable
  MockProducer
  (close ([p] (.close p))
    ([p timeout] (.close p timeout TimeUnit/SECONDS)))
  MockConsumer
  (close ([c] (.close c))))

(deftest closing
  (let [p1 (MockProducer. true (StringSerializer.) (StringSerializer.))
        p2 (MockProducer. true (StringSerializer.) (StringSerializer.))
        c (MockConsumer. (OffsetResetStrategy/EARLIEST))]
    ;; mocks do not throw on send/poll when they are closed, so sanity check:
    (close p1)
    (close p2 42)
    (close c)))

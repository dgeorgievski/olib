(ns olib.core
  (:require [clojure.java.jmx :as jmx]
            [clj-yaml.core :as yaml]
            [riemann.client :as riemann]
            [clojure.pprint :refer (pprint)]
            [taoensso.timbre :as log]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer close! pub sub thread
                     alts! alts!! timeout sliding-buffer dropping-buffer]])
  (:gen-class)
  (:import (java.net InetAddress)))

(defn- get-riemann-connection
  [riemann]
  (riemann/tcp-client riemann))

(defn publish-metrics
  [riemann]
  (let [results (chan (dropping-buffer 100))
        conn (get-riemann-connection riemann)]

    ;(log/info "Publishing to: " riemann)
    (thread (while true
              (let [events (<!! results)]
                (println "Events count: " (count events))
                (pprint events))))
                ;(riemann/send-events conn events))))
  results))

(defn now []
  (java.util.Date.))

(defn munge-credentials
  "Takes a parsed yaml config and, if it has jmx username & password,
   configures the jmx environment map properly. If only a username or
   password is set, exits with an error"
  [config]
  (let [{:keys [host port event_host username password]} (:jmx config)]
    (when (and username (not password))
      (println "Provided username but no password.")
      (System/exit 1))
    (when (and password (not username))
      (println "Provided password but no username")
      (System/exit 1))
    (if (or username password)
      (assoc config :jmx {:host host
                          :port port
                          :event_host event_host
                          :environment {"jmx.remote.credentials"
                                        (into-array String [username password])}}))
      config))

(defn expand-aregex
  [jmx queries]
  (jmx/with-connection jmx
    (doall
    (for [{:keys [obj aregex attr tags service]} queries
            a aregex]
      (let [pattern (re-pattern a)
            attlist (jmx/attribute-names obj)
            attdef (set attr)]
              ;(pprint attdef)
              {:service service
                      :obj obj
                      :tags tags
                      :attr (map name (filter #(and
                                          (re-find pattern (name %))
                                          (not (contains? attdef (name %))))
                                          attlist))})))))
(defn get-attr
  [jmx queries]
  (let [event_host (if (:event_host jmx)
                        (:event_host jmx)
                        (or (:host jmx) (.getCanonicalHostName (InetAddress/getLocalHost))))]
    (jmx/with-connection jmx
        (doall
          (for [{:keys [obj attr tags service]} queries
                a attr]
            (let [srv (if service
                      (str service \. a)
                      (str obj \. a))
                   [metric desc tags_check ok] (try
                                              [(jmx/read obj a) "" tags "ok"]
                                            (catch Exception e
                                              [-1 (.getMessage e) "error" "failed"]))]

                {:service srv
                  :host event_host
                  :description desc
                  :state ok
                  :metric metric
                  :tags tags_check}))))))

(defn read-attrs
  [jmx qatts results]
  (let [ticks-att (chan)
        host (:host jmx)]
    (thread
      (loop [t (<!! ticks-att)]
        (>!! results (try
                          (get-attr jmx qatts)
                        (catch Exception e
                          (log/error (.getMessage e))
                          { :service "Exception"
                            :host host
                          :description (.getMessage e)
                          :state "error"
                          :metric 0
                          :tags ["error"]})))

        (recur (<!! ticks-att))))
  ticks-att))

(defn read-aregex
  [jmx qaregex results]
  (let [ticks-aregex (chan)
        qexp (try
                  (expand-aregex jmx qaregex)
                (catch Exception e
                  (log/error (str "Expand aregex: " (.getMessage e)))
                  {}))
        host (:host jmx)]
    (thread
      (loop [t (<!! ticks-aregex)]
        (>!! results (try
                        (get-attr jmx qexp)
                      (catch Exception e
                        (log/error (.getMessage e))
                        { :service "Exception"
                          :host host
                          :description (str "Aregex error: " (.getMessage e))
                          :state "error"
                          :metric 0
                          :tags ["error"]})))
      (recur (<!! ticks-aregex))))
  ticks-aregex))

(defn start-config
  [config]
  (thread
    (let [yaml (yaml/parse-string (slurp config))
        munged (munge-credentials yaml)
        {:keys [jmx riemann queries]} munged
        qatts (filter #(:attr %) queries)
        cnt_qatts (count qatts)
        qaregex (filter #(:aregex %) queries)
        cnt_qaregex (count qaregex)
        interval (* 1000 (:interval riemann))
        host (:host jmx)
        ticks (chan)
        ticks-pub (pub ticks :ticks)
        results (publish-metrics riemann)]

      (if (< 0 cnt_qatts)
        (sub ticks-pub :atts (read-attrs jmx qatts results)))

      (if (< 0 cnt_qaregex)
        (sub ticks-pub :atts (read-aregex jmx qaregex results)))

      (if (or (< 0 cnt_qatts)
              (< 0 cnt_qaregex))
          (while true
            (>!! ticks {:ticks :atts :data (str host " - " (now))})
            (<!! (timeout interval)))))))
;(pprint (expand-aregex jmx qaregex))))

(defn test-config
  [config]
  (let [yaml (yaml/parse-string (slurp config))
      munged (munge-credentials yaml)
      {:keys [jmx riemann queries]} munged
      qatts (filter #(:attr %) queries)
      qaregex (filter #(:aregex %) queries)
      interval (* 1000 (:interval riemann))]

    (println (expand-aregex jmx qaregex))))
    ;(println (get-attr jmx qatts))))

(defn -main
  [& args]
  (doseq [arg args]
    (log/info "Started monitors for: " arg)
    (start-config arg))

    ; keep the threads running
   (while true
     (<!! (timeout 60000))))

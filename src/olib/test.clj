(ns olib.core
  (:require [clojure.java.jmx :as jmx]
            [clj-yaml.core :as yaml]
            [clojure.pprint :refer (pprint)]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout sliding-buffer]])
  (:gen-class)
  (:import (java.net InetAddress)))

;(def in (chan (sliding-buffer 100)))
(def results (chan))
(def config (chan))
(def metrics (chan))
(def attlist (chan))
;(def out (chan))

(thread (while true (println (<!! results))))
(thread (while true (println "attlist: " (<!! attlist))))


(defn read-aregex
  [jmx queries]
  ;; read all the attrs for
  (thread
    (jmx/with-connection jmx
      (let [fq (filter #(:aregx %) queries)]
        (while true
          (doseq [e fq]
            (let [{:keys [obj aregx tags service]} e]
              (doseq [ats (jmx/attribute-names obj)]
                (>!! attlist ats)))
          (<!! (timeout 5000))))))))

;; read jmx metrics
(defn read-attrs
  [jmx queries]
  (jmx/with-connection jmx
    (while true
      (doseq [{:keys [obj attr tags service]} queries
                a attr]
        (>!! results {:service (if service
                            (str service \. a)
                            (str obj \. a))
                    :event_host (:event_host jmx)
                    :state "ok"
                    :tags tags
                    :metric (jmx/read obj a)}))
        (<!! (timeout 5000)))))

(defn test-queries
  [yaml]
;;    (println yaml)
  (let [{:keys [jmx queries]} yaml]
    (read-attrs jmx queries)
    (read-aregex jmx queries)))

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
                                        (into-array String [username password])}})
      config)))

(defn start-config
  "Takes a path to a yaml config, parses it, and runs it in a loop"
  [config]
  (let [yaml (yaml/parse-string (slurp config))
        munged (munge-credentials yaml)
        {:keys [jmx queries]} munged]
    ;(pprint munged)))
    ;(read-attrs jmx queries)
    (read-aregex jmx queries)
    (while true (print ".") (<!! (timeout 1000)))))


(defn -main
  [& args]
  (doseq [arg args]
    (println "Started monitors")
    (start-config arg)))

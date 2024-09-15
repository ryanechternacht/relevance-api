(ns sharepage-email.middleware.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

  ;; TODO probaby use component or mount for this
(def config
  (with-open [r (io/reader "resources/config.edn")]
    (edn/read (java.io.PushbackReader. r))))

  ; This form has the advantage that changes to wrap-debug-impl are
  ; automatically reflected in the handler (due to the lookup in `wrap-config`)
(defn- wrap-config-impl [handler request]
  (handler (assoc request :config config)))

(defn wrap-config [h] (partial #'wrap-config-impl h))

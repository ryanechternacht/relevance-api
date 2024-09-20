(ns standby-api.migratus
  (:require [migratus.core :as migratus]
            [standby-api.middleware.config :as config]))
  
  (def db (:pg-db config/config))
  
  (def migratus-config {:store                :database
                        :migration-dir        "migrations/"
                        :migration-table-name "migratus"
                        :db db})
  
  ;; ;; dev
  ;; (def db {:dbtype "postgresql"
  ;;           :dbname "swaypage"
  ;;           :host "swaypage-dev.c7o2qu0iacgk.us-east-2.rds.amazonaws.com"
  ;;           :user "postgres"
  ;;           :password "Z4L25#FDM#pe"
  ;;           :ssl false})
  
  ;; prod
  ;; (def db {:dbtype "postgresql"
  ;;          :dbname "sharepage"
  ;;          :host "sharepage.c7o2qu0iacgk.us-east-2.rds.amazonaws.com"
  ;;          :user "postgres"
  ;;          :password "MUMlmURVCC9Wed9Lv79Pqi5a"
  ;;          :ssl false})
  
  (comment
    (migratus/create migratus-config "example")
    (migratus/migrate migratus-config)
    (migratus/rollback migratus-config)
    ;
    )
  
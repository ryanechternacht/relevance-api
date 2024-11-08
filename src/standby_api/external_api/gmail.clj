(ns standby-api.external-api.gmail
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [cljstache.core :as stache]
            [clojure.string :as str]
            [standby-api.data.users :as users]
            [standby-api.db :as db])
  (:import java.util.Base64))

(def email-body-template (slurp "resources/email-response.mustache"))

;; TODO remove these catches and have higher levels catch?
(defn get-access-token [{:keys [client-id client-secret]} refresh-token]
  (try
    (-> (http/post (str "https://oauth2.googleapis.com/token"
                        "?client_id=" client-id
                        "&client_secret=" client-secret
                        "&grant_type=refresh_token"
                        "&refresh_token=" refresh-token)
                   {:as :json
                    :accept :json})
        :body)
    (catch Exception ex
      (println "gmail-access-token exception")
      (println ex)
      (throw ex))))

(defn gmail-api-get [access-token url]
  (try
    (-> (http/get (str "https://www.googleapis.com/gmail/v1/" url)
                  {:as :json
                   :accept :json
                   :content-type :json
                   :oauth-token access-token})
        :body)
    (catch Exception ex
      (println "gmail-api-get exception")
      (println ex)
      (throw ex))))

(defn gmail-api-post [access-token url body]
  (try
    (-> (http/post (str "https://www.googleapis.com/gmail/v1/" url)
                   {:as :json
                    :accept :json
                    :content-type :json
                    :oauth-token access-token
                    :body (json/generate-string body)})
        :body)
    (catch Exception ex
      (println "gmail-api-post exception")
      (println ex)
      (throw ex))))

;; (defn- base64-url-decode [to-decode]
;;   (-> to-decode
;;       (str/replace #"-" "+")
;;       (str/replace #"_" "/")
;;       (#(.decode (Base64/getDecoder) %))
;;       String.))

(defn- base64-url-encode [to-decode]
  (-> to-decode
      (#(.encodeToString (Base64/getEncoder) (.getBytes %)))
      (str/replace #"\+" "-")
      (str/replace #"\/" "_")))

(defn parse-from-email
  "turns an email like 'notion <notion@notion.com>' into 'notion@notion.com'"
  [header-email]
  (-> header-email
      (str/replace #".*<" "")
      (str/replace #">" "")))

;; I'm not convinced this is quite right, but it's probably mostly right
(defn- get-domain-from-email [email]
  (-> email
      (str/split #"@")
      second
      (str/replace #">" "")))

(defn is-sender-internal? [user-email from-email]
  (= (get-domain-from-email user-email) (get-domain-from-email from-email)))

(comment
  (parse-from-email  "Notion <notify@mail.notion.so>")
  (parse-from-email  "notify@mail.notion.so")

  (get-domain-from-email "ryan@sharepage.io")
  (get-domain-from-email "Notion <notify@mail.notion.so>")

  (is-sender-internal? "ryan@sharepage.io" "tom@sharepage.io")
  (is-sender-internal? "ryan@sharepage.io" "tom@swaypage.io")
  ;
  )

(defn does-message-have-label? [message label-id]
  (->> message
       :labelIds
       (filter #(= % label-id))
       seq))

(comment
  (does-message-have-label? {:id "19206daeae5912f8",
                             :threadId "19206daeae5912f8",
                             :labelIds ["Label_6805839102955159862",
                                        "IMPORTANT",
                                        "CATEGORY_PERSONAL"]}
                            "Label_6805839102955159862")
  (does-message-have-label? {:id "19206daeae5912f8",
                             :threadId "19206daeae5912f8",
                             :labelIds ["Label_6805839102955159862",
                                        "IMPORTANT",
                                        "CATEGORY_PERSONAL"]}
                            "asdf")
  ;
  )

(defn has-prior-correspondence-with-sender? [access-token relevance-signup-date relevance-label sender-email]
  (let [formatted-signup-date (-> relevance-signup-date .toInstant .getEpochSecond)
        q (str "{{from:" sender-email " to:" sender-email "} AND before:" formatted-signup-date " to:" sender-email " AND -label:" relevance-label "}")
        url (str "/users/me/threads?q=" q)]
    (-> (gmail-api-get access-token url)
        :threads
        count
        (#(> % 0)))))

(comment
  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (let [{:keys [created_at]} (users/get-by-email db/local-db "ryan@relevance.to")]
    (has-prior-correspondence-with-sender? <access-token>
                                           created_at
                                           "Relevance"
                                           "ryan@sharepage.io"))
  ;
  )

;; (defn get-body-text
;;   "Pass in a gmail message object and this will extract the body text. This handles
;;    one part emails (where the body text is in :body) or multi-part emails where the body
;;    text is separated across multiple :parts. Finally, we will base64url decode the response
;;    to return a blob in plaintext (which will often be html)"
;;   [{{body :body parts :parts} :payload}]
;;   (if parts
;;     (->> parts
;;          (map #(get-in % [:body :data]))
;;          (map base64-url-decode)
;;          str/join)
;;     (-> body
;;         :data
;;         base64-url-decode)))

(comment
  ;; (get-body-text {:payload {:parts [{:partId "0",
  ;;                                    :body {:size 1933,
  ;;                                           :data "SGkgdGhlcmUsDQoNCkluIHRvZGF54oCZcyB3b3JsZCwgdGhlcmUgYXJlIGNvdW50bGVzcyBtZXRyaWNzIG91dCB0aGVyZSB0byBtZWFzdXJlIHByb2R1Y3Qgc3VjY2Vzcy4gVGhlIHF1ZXN0aW9uIGlz4oCTIHdoaWNoIG1ldHJpY3MgbWF0dGVyIG1vc3Q_DQoNCkluc3RlYWQgb2YgbWFraW5nIHlvdSBndWVzcywgaGVyZeKAmXMgYSB0cmllZCBhbmQgdGVzdGVkIGZyYW1ld29yayB0aGF0IHdpbGwgaGVscCB5b3UgdHJhY2sgdGhlIDUga2V5IGluZGljYXRvcnMgb2YgcHJvZHVjdCBvciB3ZWJzaXRlIHN1Y2Nlc3M6DQoNCg0KCS0gQnJlYWR0aA0KCS0gRGVwdGgNCgktIFVzZWFiaWxpdHkNCgktIEZyZXF1ZW5jeQ0KCS0gU2VudGltZW50DQpQYWNrZWQgd2l0aCBpbmR1c3RyeSB0aXBzLCBzaW1wbGUgYnJlYWtkb3ducywgYW5kIG1ldHJpY3MgdG8gYXZvaWQsIHRoaXMgZ3VpZGUgaXMgaGVyZSB0byBoZWxwIHlvdSBtZWFzdXJlIGFuZCBpbXByb3ZlIHlvdXIgcHJvZHVjdCBhbmQgb3ZlcmFsbCBidXNpbmVzcyBwZXJmb3JtYW5jZS4NCg0KVG8gYWNjZXNzIHlvdXIgY29weSBvZiB0aGUgZ3VpZGUsIHNpbXBseSBjbGljayBiZWxvdy4NCkJlc3QsDQpDb25uaWUNCkhlYXAgaXMgdGhlIGZ1dHVyZSBvZiBkaWdpdGFsIGluc2lnaHRzLiBCYWNrZWQgYnkgdGhlIG1vc3QgY29tcGxldGUgZGF0YXNldCwgd2UgaWxsdW1pbmF0ZSBoaWRkZW4gaW5zaWdodHMgc28gdGVhbXMgY2FuIGNyZWF0ZSB0aGUgYmVzdCBwb3NzaWJsZSBkaWdpdGFsIGV4cGVyaWVuY2VzIHRvIGFjY2VsZXJhdGUgdGhlaXIgYnVzaW5lc3MuDQpQcm9kdWN0cyA8aHR0cHM6Ly9tYWlsLmhlYXBhbmFseXRpY3MuY29tL05qSXlMVmhKVUMwNE16Y0FBQUdWZ05oN2JUa19JdWRfMnI2U09OeVRCdE5aRGdqdHhHRzRTMGwwNE4tZTlXOGZaYl9odXE0T0V5M0xoTlpncnBiVGFTeS13YVE9Pg0KU29sdXRpb25zIDxodHRwczovL21haWwuaGVhcGFuYWx5dGljcy5jb20vTmpJeUxWaEpVQzA0TXpjQUFBR1ZnTmg3YmNfOTg3NUctbm5icFNOM2NZVWlVcTBVX24wX2V3Qm91SHJvUkVpNTh4QWxTN3JpT3hPcXBIZzNrNVo5ZW5VazkzOD0-DQpDdXN0b21lcnMgPGh0dHBzOi8vbWFpbC5oZWFwYW5hbHl0aWNzLmNvbS9Oakl5TFZoSlVDMDRNemNBQUFHVmdOaDdiYjk5YjIzWmxkREdDREVuaEJXZnA3MFZYZmg3MW1FNEZaTXhKRHdHaEgzQW9hU2VKVEUzLUw5VTR0bG01czJ5aVM0PT4NClByaWNpbmcgPGh0dHBzOi8vbWFpbC5oZWFwYW5hbHl0aWNzLmNvbS9Oakl5TFZoSlVDMDRNemNBQUFHVmdOaDdiUk9vUnlFUi1jQm1XSW9RaUt6M3VoQW81c2NuS3NnQWxxSXhydjdXRGFNZE1QWG9OcVQ2WTRKRGgtbUI2X2VISVd3PT4NClJlc291cmNlcyA8aHR0cHM6Ly9tYWlsLmhlYXBhbmFseXRpY3MuY29tL05qSXlMVmhKVUMwNE16Y0FBQUdWZ05oN2JSbk41V1RuLWozMkJYM3lJVjZmc0tyUXNXa0tMdTFzWHB4RURaSGQ3SXJZVDNTaDZuVWU0NG9TSjFURkZXTWRWVG89Pg0KUGFydG5lcnMgPGh0dHBzOi8vbWFpbC5oZWFwYW5hbHl0aWNzLmNvbS9Oakl5TFZoSlVDMDRNemNBQUFHVmdOaDdiY0FEZXBUMFhrMFh3ZldZcURrRmNxdVAtbzZpNVpNNGRVc1dhV0JNZS0wWUtEaXMxdC1DVTJzSGkwVTJmZk1CbW9JPT4NCg0KVGhpcyBlbWFpbCB3YXMgc2VudCB0byByeWFuQHNoYXJlcGFnZS5pby4gSWYgeW91IG5vIGxvbmdlciB3aXNoIHRvIHJlY2VpdmUgdGhlc2UgZW1haWxzIHlvdSBtYXkgdW5zdWJzY3JpYmUgaGVyZTogaHR0cHM6Ly9pbmZvLmhlYXAuaW8vVW5zdWJzY3JpYmVQYWdlLmh0bWw_bWt0X3Vuc3Vic2NyaWJlPTEmbWt0X3Rvaz1Oakl5TFZoSlVDMDRNemNBQUFHVmdOaDdiZnFha2xSNWRaelVqYWJuRzI2ZzhkaVp4a3BQZWZtbVVIUVNocHR5S09vNGFteGpncGhlM3VNZlZoa01yYnNsSXZMWlplSmFPc3ZLaDV2N3czQk9SU3JMNzVGOGtYYmpOZTVPM3dOZURvSS4NCg=="}},
  ;;                                   {:partId "1",
  ;;                                    :body {:size 27092,
  ;;                                           :data "PCFkb2N0eXBlIGh0bWw-DQo8aHRtbCBsYW5nPSJlbiIgeG1sbnM6bz0idXJuOnNjaGVtYXMtbWljcm9zb2Z0LWNvbTpvZmZpY2U6b2ZmaWNlIiB4bWxuczp2PSJ1cm46c2NoZW1hcy1taWNyb3NvZnQtY29tOnZtbCI-DQo8aGVhZD4gDQo8dGl0bGU-PC90aXRsZT4gDQo8bWV0YSBuYW1lPSJjb2xvci1zY2hlbWUiIGNvbnRlbnQ9ImxpZ2h0IGRhcmsiPiANCjxtZXRhIG5hbWU9InN1cHBvcnRlZC1jb2xvci1zY2hlbWVzIiBjb250ZW50PSJsaWdodCBkYXJrIj4gDQo8IS0tIEdsb2JhbCBWYXJpYWJsZXMgLS0-ICAgICAgICAgIA0KPCEtLSBMb2NhbCBWYXJpYWJsZXMgLS0-ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICANCjxtZXRhIGNvbnRlbnQ9InRleHQvaHRtbDsgY2hhcnNldD11dGYtOCIgaHR0cC1lcXVpdj0iQ29udGVudC1UeXBlIj4gDQo8bWV0YSBjb250ZW50PSJ3aWR0aD1kZXZpY2Utd2lkdGgsIGluaXRpYWwtc2NhbGU9MS4wIiBuYW1lPSJ2aWV3cG9ydCI-IA0KPCEtLVtpZiBtc29dPg0KICAgICAgPHhtbD4NCiAgICAgICAgIDxvOk9mZmljZURvY3VtZW50U2V0dGluZ3M-DQogICAgICAgICAgICA8bzpQaXhlbHNQZXJJbmNoPjk2PC9vOlBpeGVsc1BlckluY2g-DQogICAgICAgICAgICA8bzpBbGxvd1BORy8-DQogICAgICAgICA8L286T2ZmaWNlRG9jdW1lbnRTZXR0aW5ncz4NCiAgICAgIDwveG1sPg0KICAgICAgPCFbZW5kaWZdLS0-IA0KPHN0eWxlPg0KICAgICAgICAgKiB7DQogICAgICAgICBib3gtc2l6aW5nOiBib3JkZXItYm94Ow0KICAgICAgICAgfQ0KICAgICAgICAgYm9keSB7DQogICAgICAgICBtYXJnaW46IDA7DQogICAgICAgICBwYWRkaW5nOiAwOw0KICAgICAgICAgfQ0KICAgICAgICAgYm9keSBwIGEgew0KICAgICAgICAgICAgY29sb3I6ICMzMUQ4OTEgIWltcG9ydGFudDsNCiAgICAgICAgICAgIGZvbnQtc2l6ZTogaW5oZXJpdCAhaW1wb3J0YW50Ow0KICAgICAgICAgICAgZm9udC1mYW1pbHk6IGluaGVyaXQgIWltcG9ydGFudDsNCiAgICAgICAgICAgIGZvbnQtd2VpZ2h0OiBpbmhlcml0ICFpbXBvcnRhbnQ7DQogICAgICAgICAgICBsaW5lLWhlaWdodDogaW5oZXJpdCAhaW1wb3J0YW50Ow0KICAgICAgICB9DQogICAgICAgICAgYVt4LWFwcGxlLWRhdGEtZGV0ZWN0b3JzXSB7DQogICAgICAgICAgICBjb2xvcjogIzMxRDg5MSAhaW1wb3J0YW50Ow0KICAgICAgICAgICAgZm9udC1zaXplOiBpbmhlcml0ICFpbXBvcnRhbnQ7DQogICAgICAgICAgICBmb250LWZhbWlseTogaW5oZXJpdCAhaW1wb3J0YW50Ow0KICAgICAgICAgICAgZm9udC13ZWlnaHQ6IGluaGVyaXQgIWltcG9ydGFudDsNCiAgICAgICAgICAgIGxpbmUtaGVpZ2h0OiBpbmhlcml0ICFpbXBvcnRhbnQ7DQogICAgICAgIH0NCiAgICAgICAgdSArICNib2R5IHAgYSB7DQogICAgICAgICAgICBjb2xvcjogIzMxRDg5MSAhaW1wb3J0YW50Ow0KICAgICAgICAgICAgZm9udC1zaXplOiBpbmhlcml0ICFpbXBvcnRhbnQ7DQogICAgICAgICAgICBmb250LWZhbWlseTogaW5oZXJpdCAhaW1wb3J0YW50Ow0KICAgICAgICAgICAgZm9udC13ZWlnaHQ6IGluaGVyaXQgIWltcG9ydGFudDsNCiAgICAgICAgICAgIGxpbmUtaGVpZ2h0OiBpbmhlcml0ICFpbXBvcnRhbnQ7DQogICAgICAgIH0NCiAgICAgICAgI01lc3NhZ2VWaWV3Qm9keSBwIGEgew0KICAgICAgICAgICAgY29sb3I6ICMzMUQ4OTEgIWltcG9ydGFudDsNCiAgICAgICAgICAgIGZvbnQtc2l6ZTogaW5oZXJpdCAhaW1wb3J0YW50Ow0KICAgICAgICAgICAgZm9udC1mYW1pbHk6IGluaGVyaXQgIWltcG9ydGFudDsNCiAgICAgICAgICAgIGZvbnQtd2VpZ2h0OiBpbmhlcml0ICFpbXBvcnRhbnQ7DQogICAgICAgICAgICBsaW5lLWhlaWdodDogaW5oZXJpdCAhaW1wb3J0YW50Ow0KICAgICAgICB9DQogICAgICAgICBwIHsNCiAgICAg"}}]}})

  ;; (get-body-text {:payload {:body {:size 10328,
  ;;                                  :data "PCFET0NUWVBFIGh0bWwgUFVCTElDICItLy9XM0MvL0RURCBYSFRNTCAxLjAgU3RyaWN0Ly9FTiIgImh0dHA6Ly93d3cudzMub3JnL1RSL3hodG1sMS9EVEQveGh0bWwxLXN0cmljdC5kdGQiPgogICAgICA8aHRtbAogICAgICAgIAogICAgICAgIGRpcj0ibHRyIgogICAgICAgIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hodG1sIgogICAgICAgIHhtbG5zOnY9InVybjpzY2hlbWFzLW1pY3Jvc29mdC1jb206dm1sIgogICAgICAgIHhtbG5zOm89InVybjpzY2hlbWFzLW1pY3Jvc29mdC1jb206b2ZmaWNlOm9mZmljZSI-CiAgICAgICAgPGhlYWQ-CiAgICAgICAgICA8bWV0YSBodHRwLWVxdWl2PSJDb250ZW50LVR5cGUiIGNvbnRlbnQ9InRleHQvaHRtbDsgY2hhcnNldD11dGYtOCIgLz4KICAgICAgICAgIDxtZXRhIGh0dHAtZXF1aXY9IlgtVUEtQ29tcGF0aWJsZSIgY29udGVudD0iSUU9ZWRnZSIgLz4KICAgICAgICAgIDxtZXRhIG5hbWU9InZpZXdwb3J0IiBjb250ZW50PSJ3aWR0aD1kZXZpY2Utd2lkdGgiLz4KCiAgICAgICAgICA8dGl0bGU-IDwvdGl0bGU-CgogICAgICAgICAgPHN0eWxlIHR5cGU9InRleHQvY3NzIj4KICAgICAgICAgICAgLm5vdGlvbi1lbWFpbCx0ZHtmb250LWZhbWlseTotYXBwbGUtc3lzdGVtLEJsaW5rTWFjU3lzdGVtRm9udCxTZWdvZSBVSSxSb2JvdG8sT3h5Z2VuLFVidW50dSxDYW50YXJlbGwsRmlyYSBTYW5zLERyb2lkIFNhbnMsSGVsdmV0aWNhIE5ldWUsc2Fucy1zZXJpZjstd2Via2l0LWZvbnQtc21vb3RoaW5nOnN1YnBpeGVsLWFudGlhbGlhc2VkfS5ub3Rpb24tZW1haWx7dGV4dC1hbGlnbjpsZWZ0O2xpbmUtaGVpZ2h0OjEuNTttYXgtd2lkdGg6NjAwcHg7cGFkZGluZy10b3A6MzJweDtwYWRkaW5nLWxlZnQ6NjRweDtwYWRkaW5nLXJpZ2h0OjY0cHh9QG1lZGlhIG9ubHkgc2NyZWVuIGFuZCAobWF4LWRldmljZS13aWR0aDo0ODBweCl7Lm5vdGlvbi1lbWFpbHtwYWRkaW5nLXRvcDowO3BhZGRpbmctbGVmdDoxNnB4O3BhZGRpbmctcmlnaHQ6MTZweH19Lm5vdGlvbi1lbWFpbC1idXR0b24taG92ZXJ7dHJhbnNpdGlvbjpiYWNrZ3JvdW5kIDE0MG1zIGVhc2UtaW59Lm5vdGlvbi1lbWFpbC1idXR0b24taG92ZXI6aG92ZXJ7YmFja2dyb3VuZDpyZ2JhKDU4LDU2LDUyLC4wOCl9CgogICAgICAgICAgICAjX19ib2R5VGFibGVfXyB7CiAgICAgICAgICAgICAgbWFyZ2luOiAwOwogICAgICAgICAgICAgIHBhZGRpbmc6IDA7CiAgICAgICAgICAgICAgd2lkdGg6IDEwMCUgIWltcG9ydGFudDsKICAgICAgICAgICAgfQogICAgICAgICAgPC9zdHlsZT4KCiAgICAgICAgICA8IS0tW2lmIGd0ZSBtc28gOV0-CiAgICAgICAgICAgIDx4bWw-CiAgICAgICAgICAgICAgPG86T2ZmaWNlRG9jdW1lbnRTZXR0aW5ncz4KICAgICAgICAgICAgICAgIDxvOkFsbG93UE5HLz4KICAgICAgICAgICAgICAgIDxvOlBpeGVsc1BlckluY2g-OTY8L286UGl4ZWxzUGVySW5jaD4KICAgICAgICAgICAgICA8L286T2ZmaWNlRG9jdW1lbnRTZXR0aW5ncz4KICAgICAgICAgICAgPC94bWw-CiAgICAgICAgICA8IVtlbmRpZl0tLT4KICAgICAgICA8L2hlYWQ-CiAgICAgICAgPGJvZHkgYmdjb2xvcj0iI0ZGRkZGRiIgd2lkdGg9IjEwMCUiIHN0eWxlPSItd2Via2l0LWZvbnQtc21vb3RoaW5nOiBhbnRpYWxpYXNlZDsgd2lkdGg6MTAwJSAhaW1wb3J0YW50OyBiYWNrZ3JvdW5kOiNGRkZGRkY7LXdlYmtpdC10ZXh0LXNpemUtYWRqdXN0Om5vbmU7IG1hcmdpbjowOyBwYWRkaW5nOjA7IG1pbi13aWR0aDoxMDAlOyBkaXJlY3Rpb246IGx0cjsiPg0KPGRpdiBzdHlsZT0iY29sb3I6dHJhbnNwYXJlbnQ7dmlzaWJpbGl0eTpoaWRkZW47b3BhY2l0eTowO2ZvbnQtc2l6ZTowcHg7Ym9yZGVyOjA7bWF4LWhlaWdodDoxcHg7d2lkdGg6MXB4O21hcmdpbjowcHg7cGFkZGluZzowcHg7Ym9yZGVyLXdpZHRoOjBweCFpbXBvcnRhbnQ7ZGlzcGxheTpub25lIWltcG9ydGFudDtsaW5lLWhlaWdodDowcHghaW1wb3J0YW50OyI-PGltZyBib3JkZXI9IjAiIHdpZHRoPSIxIiBoZWlnaHQ9IjEiIHNyYz0iaHR0cDovL3NwLm1haWwubm90aW9uLnNvL3EvWE14djFlYTJxVlVnX25od3RpdFl3QX5-L0FBUVl6Z0F-L1JnUm94YWd2UFZjRGMzQmpRZ3BtNFM4ajQyYU1fRy1UVWhGeWVXRnVRSE5vWVhKbGNHRm5aUzVwYjFnRUFBQUFBQX5-IiBhbHQ9IiIvPjwvZGl2Pg0KCiAgICAgICAgICA8dGFibGUgYmdjb2xvcj0iI0ZGRkZGRiIgaWQ9Il9fYm9keVRhYmxlX18iIHdpZHRoPSIxMDAlIiBzdHlsZT0iLXdlYmtpdC1mb250LXNtb290aGluZzogYW50aWFsaWFzZWQ7IHdpZHRoOjEwMCUgIWltcG9ydGFudDsgYmFja2dyb3VuZDojRkZGRkZGOy13ZWJraXQtdGV4dC1zaXplLWFkanVzdDpub25lOyBtYXJnaW46MDsgcGFkZGluZzowOyBtaW4td2lkdGg6MTAwJSI-CiAgICAgICAgICAgIDx0cj4KICAgICAgICAgICAgICA8dGQgYWxpZ249ImNlbnRlciI-CiAgICAgICAgICAgICAgICA8c3BhbiBzdHlsZT0iZGlzcGxheTogbm9uZSAhaW1wb3J0YW50OyBjb2xvcjogI0ZGRkZGRjsgbWFyZ2luOjA7IHBhZGRpbmc6MDsgZm9udC1zaXplOjFweDsgbGluZS1oZWlnaHQ6MXB4OyI-IDwvc3Bhbj4KICAgICAgICAgICAgICAgIDxkaXYgY2xhc3M9Im5vdGlvbi1lbWFpbCI-PGRpdiBzdHlsZT0iZGlzcGxheTpub25lO3Zpc2liaWxpdHk6aGlkZGVuO2ZvbnQtc2l6ZToxcHg7Y29sb3I6I2ZmZmZmZjtsaW5lLWhlaWdodDoxcHg7bWF4LWhlaWdodDowcHg7bWF4LXdpZHRoOjBweDtvcGFjaXR5OjA7b3ZlcmZsb3c6aGlkZGVuIiBjbGFzcz0ibm90aW9uLWVtYWlsLXByZS1oZWFkZXIiPlRyZW5kaW5nIGFuZCBzdWdnZXN0ZWQgZm9yIHlvdTwvZGl2PjxkaXYgc3R5bGU9ImRpc3BsYXk6bm9uZTt2aXNpYmlsaXR5OmhpZGRlbjtmb250LXNpemU6MXB4O2NvbG9yOiNmZmZmZmY7bGluZS1oZWlnaHQ6MXB4O21heC1oZWlnaHQ6MHB4O21heC13aWR0aDowcHg7b3BhY2l0eTowO292ZXJmbG93OmhpZGRlbiI-zY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtIM2PIOKAjCDCoCDigIcgwq0gzY8g4oCMIMKgIOKAhyDCrSDNjyDigIwgwqAg4oCHIMKtPC9kaXY-PGRpdiBzdHlsZT0iY29sb3I6IzMzMzMzMztmb250LXNpemU6MTJweCI-PGRpdiBzdHlsZT0ibWFyZ2luLXRvcDozOHB4Ij48dGFibGUgc3R5bGU9ImJvcmRlci1jb2xsYXBzZTpjb2xsYXBzZSI-PHRib2R5Pjx0cj48dGQ-PGltZyBzdHlsZT0idmVydGljYWwtYWxpZ246dG9wIiBzcmM9Imh0dHBzOi8vczMtdXMtd2VzdC0yLmFtYXpvbmF3cy5jb20vcHVibGljLm5vdGlvbi1zdGF0aWMuY29tL2NhN2IzOTVmLTY4MmQtNDgxYi1hOWYwLTQwYzQ2MjQ1ZWEyNy9zaGFyZXBhZ2VfbG9nb19ibHVlX2NpcmNsZV8xMjBweC5wbmciIHdpZHRoPSIyOCIvPjwvdGQ-PHRkIHN0eWxlPSJ0ZXh0LWFsaWduOmxlZnQ7cGFkZGluZzowcHggMjBweCAwcHggMTJweDt3aWR0aDoxNTBweDt3aGl0ZS1zcGFjZTpub3dyYXA7ZGlzcGxheTpibG9jayI-PGRpdiBzdHlsZT0iY29sb3I6Izc4Nzc3NDtwYWRkaW5nLXRvcDo1cHg7b3ZlcmZsb3c6aGlkZGVuO3RleHQtb3ZlcmZsb3c6ZWxsaXBzaXM7Zm9udC1zaXplOjE0cHg7Zm9udC13ZWlnaHQ6NTkwIj5TaGFyZXBhZ2U8L2Rpdj48L3RkPjwvdHI-PC90Ym9keT48L3RhYmxlPjwvZGl2PjwvZGl2PjxoMSBzdHlsZT0iY29sb3I6IzMzMztmb250LXNpemU6MjBweCI-WW91ciBOb3Rpb24gZGlnZXN0PC9oMT48ZGl2IHN0eWxlPSJmb250LXNpemU6MTRweCI-UmVhZHkgdG8ganVtcCBiYWNrIGluPyBDYXRjaCB1cCBvbiB3aGF0JiN4Mjc7cyBoYXBwZW5lZCBpbiBTaGFyZXBhZ2UuPC9kaXY-PGRpdiBzdHlsZT0iaGVpZ2h0OjIwcHgiPjwvZGl2Pjx0YWJsZSBzdHlsZT0id2lkdGg6MTAwJTtib3JkZXItc3BhY2luZzowIj48dGJvZHk-PHRyPjx0ZCBzdHlsZT0iYm9yZGVyLWJvdHRvbToxcHggc29saWQgcmdiYSgyMjcsIDIyNiwgMjI0LCAwLjUpO21heC13aWR0aDo3MHZ3O21pbi13aWR0aDozNTBweCI-PGEgaHJlZj0iaHR0cHM6Ly93d3cubm90aW9uLnNvL1NoYXJlYmFzZS1kNmYyMmU2ZDMzYWI0NmJiODJhYzQyNTNkNDZmNGUzNT9wdnM9MTAzJm49ZW1haWxfZGlnZXN0X2hvbWVfdHJlbmRpbmcmbj1lbWFpbF9kaWdlc3RfaG9tZV8xIiBzdHlsZT0iZGlzcGxheTpibG9jaztjb2xvcjppbmhlcml0O3RleHQtZGVjb3JhdGlvbjpub25lO2N1cnNvcjpwb2ludGVyO3BhZGRpbmc6MTJweCAwO3dpZHRoOjEwMCUiIGNsYXNzPSJub3Rpb24tZW1haWwtYnV0dG9uLWhvdmVyIj48dGFibGUgc3R5bGU9IndpZHRoOjEwMCUiPjx0Ym9keT48dHI-PHRkIHN0eWxlPSJ3aWR0aDozNHB4O21pbi13aWR0aDozNHB4O3ZlcnRpY2FsLWFsaWduOnRvcDtwYWRkaW5nLXRvcDoycHgiPjxzcGFuIHN0eWxlPSJ2ZXJ0aWNhbC1hbGlnbjptaWRkbGU7ZGlzcGxheTppbmxpbmUtYmxvY2s7d2lkdGg6MjRweDt0ZXh0LWFsaWduOmNlbnRlcjtmb250LXNpemU6MTZweCI-8J-ThDwvc3Bhbj48L3RkPjx0ZCBzdHlsZT0icGFkZGluZy1ib3R0b206NHB4Ij48c3BhbiBzdHlsZT0iZGlzcGxheTppbmxpbmUtYmxvY2s7Zm9udC1zaXplOjE3cHg7bGluZS1oZWlnaHQ6MjRweDtmb250LXdlaWdodDo2MDA7Y29sb3I6IzMzMzMzMzt2ZXJ0aWNhbC1hbGlnbjptaWRkbGU7bWFyZ2luLXJpZ2h0OjhweCI-U2hhcmViYXNlPC9zcGFuPjwvdGQ-PC90cj48dHI-PHRkPjwvdGQ-PHRkPjxkaXYgc3R5bGU9ImNvbG9yOiM3ODc3NzQ7Zm9udC1zaXplOjE0cHg7bGluZS1oZWlnaHQ6MjBweCI-Q3JlYXRlZCBieSBUb20gU2luZ2VsbDwvZGl2PjwvdGQ-PC90cj48L3Rib2R5PjwvdGFibGU-PC9hPjwvdGQ-PC90cj48dHI-PHRkIHN0eWxlPSJib3JkZXItYm90dG9tOjFweCBzb2xpZCByZ2JhKDIyNywgMjI2LCAyMjQsIDAuNSk7bWF4LXdpZHRoOjcwdnc7bWluLXdpZHRoOjM1MHB4Ij48YSBocmVmPSJodHRwczovL3d3dy5ub3Rpb24uc28vUXVpY2stMi0wLTliNzMxNmQ5YTYyZDQ1Zjc4MDZmYzM1NjQ2Yzg0NDViP3B2cz0xMDMmbj1lbWFpbF9kaWdlc3RfaG9tZV90cmVuZGluZyZuPWVtYWlsX2RpZ2VzdF9ob21lXzIiIHN0eWxlPSJkaXNwbGF5OmJsb2NrO2NvbG9yOmluaGVyaXQ7dGV4dC1kZWNvcmF0aW9uOm5vbmU7Y3Vyc29yOnBvaW50ZXI7cGFkZGluZzoxMnB4IDA7d2lkdGg6MTAwJSIgY2xhc3M9Im5vdGlvbi1lbWFpbC1idXR0b24taG92ZXIiPjx0YWJsZSBzdHlsZT0id2lkdGg6MTAwJSI-PHRib2R5Pjx0cj48dGQgc3R5bGU9IndpZHRoOjM0cHg7bWluLXdpZHRoOjM0cHg7dmVydGljYWwtYWxpZ246dG9wO3BhZGRpbmctdG9wOjJweCI-PHNwYW4gc3R5bGU9InZlcnRpY2FsLWFsaWduOm1pZGRsZTtkaXNwbGF5OmlubGluZS1ibG9jazt3aWR0aDoyNHB4O3RleHQtYWxpZ246Y2VudGVyO2ZvbnQtc2l6ZToxNnB4Ij7wn6qEPC9zcGFuPjwvdGQ-PHRkIHN0eWxlPSJwYWRkaW5nLWJvdHRvbTo0cHgiPjxzcGFuIHN0eWxlPSJkaXNwbGF5OmlubGluZS1ibG9jaztmb250LXNpemU6MTdweDtsaW5lLWhlaWdodDoyNHB4O2ZvbnQtd2VpZ2h0OjYwMDtjb2xvcjojMzMzMzMzO3ZlcnRpY2FsLWFsaWduOm1pZGRsZTttYXJnaW4tcmlnaHQ6OHB4Ij5RdWljayAyLjA8L3NwYW4-PC90ZD48L3RyPjx0cj48dGQ-PC90ZD48dGQ-PGRpdiBzdHlsZT0iY29sb3I6Izc4Nzc3NDtmb250LXNpemU6MTRweDtsaW5lLWhlaWdodDoyMHB4Ij5DcmVhdGVkIGJ5IFRvbSBTaW5nZWxsPC9kaXY-PC90ZD48L3RyPjwvdGJvZHk-PC90YWJsZT48L2E-PC90ZD48L3RyPjx0cj48dGQgc3R5bGU9ImJvcmRlci1ib3R0b206MXB4IHNvbGlkIHJnYmEoMjI3LCAyMjYsIDIyNCwgMC41KTttYXgtd2lkdGg6NzB2dzttaW4td2lkdGg6MzUwcHgiPjxhIGhyZWY9Imh0dHBzOi8vd3d3Lm5vdGlvbi5zby9VSy1PdXRyZWFjaC1mZDg5MGE0MGE5MjU0NmMzYWE0NDQ1ZDgxZWY4ODBlZj9wdnM9MTAzJm49ZW1haWxfZGlnZXN0X2hvbWVfdHJlbmRpbmcmbj1lbWFpbF9kaWdlc3RfaG9tZV8zIiBzdHlsZT0iZGlzcGxheTpibG9jaztjb2xvcjppbmhlcml0O3RleHQtZGVjb3JhdGlvbjpub25lO2N1cnNvcjpwb2ludGVyO3BhZGRpbmc6MTJweCAwO3dpZHRoOjEwMCUiIGNsYXNzPSJub3Rpb24tZW1haWwtYnV0dG9uLWhvdmVyIj48dGFibGUgc3R5bGU9IndpZHRoOjEwMCUiPjx0Ym9keT48dHI-PHRkIHN0eWxlPSJ3aWR0aDozNHB4O21pbi13aWR0aDozNHB4O3ZlcnRpY2FsLWFsaWduOnRvcDtwYWRkaW5nLXRvcDoycHgiPjxzcGFuIHN0eWxlPSJ2ZXJ0aWNhbC1hbGlnbjptaWRkbGU7ZGlzcGxheTppbmxpbmUtYmxvY2s7d2lkdGg6MjRweDt0ZXh0LWFsaWduOmNlbnRlcjtmb250LXNpemU6MTZweCI-8J-HrPCfh6c8L3NwYW4-PC90ZD48dGQgc3R5bGU9InBhZGRpbmctYm90dG9tOjRweCI-PHNwYW4gc3R5bGU9ImRpc3BsYXk6aW5saW5lLWJsb2NrO2ZvbnQtc2l6ZToxN3B4O2xpbmUtaGVpZ2h0OjI0cHg7Zm9udC13ZWlnaHQ6NjAwO2NvbG9yOiMzMzMzMzM7dmVydGljYWwtYWxpZ246bWlkZGxlO21hcmdpbi1yaWdodDo4cHgiPlVLIE91dHJlYWNoIDwvc3Bhbj48L3RkPjwvdHI-PHRyPjx0ZD48L3RkPjx0ZD48ZGl2IHN0eWxlPSJjb2xvcjojNzg3Nzc0O2ZvbnQtc2l6ZToxNHB4O2xpbmUtaGVpZ2h0OjIwcHgiPkNyZWF0ZWQgYnkgVG9tIFNpbmdlbGw8L2Rpdj48L3RkPjwvdHI-PC90Ym9keT48L3RhYmxlPjwvYT48L3RkPjwvdHI-PHRyPjx0ZCBzdHlsZT0ibWF4LXdpZHRoOjcwdnc7bWluLXdpZHRoOjM1MHB4Ij48YSBocmVmPSJodHRwczovL3d3dy5ub3Rpb24uc28vVmlkZW8tNDJjODg3OGU1NTdmNDg1ZWE1MWJlZmY1ZmM4NmI3YTg_cHZzPTEwMyZuPWVtYWlsX2RpZ2VzdF9ob21lX3RyZW5kaW5nJm49ZW1haWxfZGlnZXN0X2hvbWVfNCIgc3R5bGU9ImRpc3BsYXk6YmxvY2s7Y29sb3I6aW5oZXJpdDt0ZXh0LWRlY29yYXRpb246bm9uZTtjdXJzb3I6cG9pbnRlcjtwYWRkaW5nOjEycHggMDt3aWR0aDoxMDAlIiBjbGFzcz0ibm90aW9uLWVtYWlsLWJ1dHRvbi1ob3ZlciI-PHRhYmxlIHN0eWxlPSJ3aWR0aDoxMDAlIj48dGJvZHk-PHRyPjx0ZCBzdHlsZT0id2lkdGg6MzRweDttaW4td2lkdGg6MzRweDt2ZXJ0aWNhbC1hbGlnbjp0b3A7cGFkZGluZy10b3A6MnB4Ij48c3BhbiBzdHlsZT0idmVydGljYWwtYWxpZ246bWlkZGxlO2Rpc3BsYXk6aW5saW5lLWJsb2NrO3dpZHRoOjI0cHg7dGV4dC1hbGlnbjpjZW50ZXI7Zm9udC1zaXplOjE2cHgiPvCfk7w8L3NwYW4-PC90ZD48dGQgc3R5bGU9InBhZGRpbmctYm90dG9tOjRweCI-PHNwYW4gc3R5bGU9ImRpc3BsYXk6aW5saW5lLWJsb2NrO2ZvbnQtc2l6ZToxN3B4O2xpbmUtaGVpZ2h0OjI0cHg7Zm9udC13ZWlnaHQ6NjAwO2NvbG9yOiMzMzMzMzM7dmVydGljYWwtYWxpZ246bWlkZGxlO21hcmdpbi1yaWdodDo4cHgiPlZpZGVvPC9zcGFuPjwvdGQ-PC90cj48dHI-PHRkPjwvdGQ-PHRkPjxkaXYgc3R5bGU9ImNvbG9yOiM3ODc3NzQ7Zm9udC1zaXplOjE0cHg7bGluZS1oZWlnaHQ6MjBweCI-Q3JlYXRlZCBieSBUb20gU2luZ2VsbDwvZGl2PjwvdGQ-PC90cj48L3Rib2R5PjwvdGFibGU-PC9hPjwvdGQ-PC90cj48L3Rib2R5PjwvdGFibGU-PGRpdiBzdHlsZT0iaGVpZ2h0OjE2cHgiPjwvZGl2PjxkaXYgc3R5bGU9Im1hcmdpbi10b3A6MzBweCI-PHRhYmxlIHN0eWxlPSJ3aWR0aDoxMDAlO2JvcmRlci1jb2xsYXBzZTpjb2xsYXBzZSI-PHRib2R5Pjx0cj48dGQgc3R5bGU9InRleHQtYWxpZ246Y2VudGVyO3ZlcnRpY2FsLWFsaWduOnRvcCI-PGEgc3R5bGU9ImJhY2tncm91bmQtY29sb3I6IzIzODNFMjtib3JkZXItcmFkaXVzOjhweDtjb2xvcjojZmZmO3RleHQtZGVjb3JhdGlvbjpub25lO3BhZGRpbmc6MTJweCAwcHg7dGV4dC1hbGlnbjpjZW50ZXI7Ym9yZGVyOjFweCBzb2xpZCAjMzA4YmJmO2Rpc3BsYXk6aW5saW5lLWJsb2NrO3dpZHRoOjEwMCU7bWluLXdpZHRoOjM1MHB4IiBocmVmPSJodHRwczovL3d3dy5ub3Rpb24uc28vaG9tZT9uPWVtYWlsX2RpZ2VzdF9ob21lJnB2cz0xMDMiPjxiIGNsYXNzPSJjb250ZXh0dWFsLWludml0ZS1lbWFpbC1jdGEiIHN0eWxlPSJmb250LXdlaWdodDo0MDA7Zm9udC1zaXplOjE2cHgiPkdvIHRvIE5vdGlvbiDihpI8L2I-PC9hPjwvdGQ-PC90cj48L3Rib2R5PjwvdGFibGU-PC9kaXY-PGRpdiBzdHlsZT0iaGVpZ2h0OjMycHgiPjwvZGl2PjxkaXYgc3R5bGU9ImZvbnQtc2l6ZToxMnB4O21hcmdpbi10b3A6MjZweDttYXJnaW4tYm90dG9tOjQycHg7Y29sb3I6Izg4ODt0ZXh0LWFsaWduOmxlZnQiPjxkaXYgc3R5bGU9ImRpc3BsYXk6ZmxleDttYXJnaW4tYm90dG9tOjE2cHgiPjxpbWcgaGVpZ2h0PSI0MCIgd2lkdGg9IjQwIiBjbGFzcz0ibm90aW9uLWVtYWlsLWxvZ28iIHNyYz0iaHR0cHM6Ly93d3cubm90aW9uLnNvL2ltYWdlcy9sb2dvLWZvci1zbGFjay1pbnRlZ3JhdGlvbi5wbmciIHN0eWxlPSJkaXNwbGF5OmJsb2NrO21hcmdpbi1ib3R0b206OHB4Ii8-PGRpdiBzdHlsZT0icGFkZGluZy1sZWZ0OjE2cHgiPjxkaXYgc3R5bGU9ImZvbnQtc3R5bGU6bm9ybWFsO2ZvbnQtd2VpZ2h0OjU5MDtmb250LXNpemU6MTRweDtsaW5lLWhlaWdodDoxOHB4O2xldHRlci1zcGFjaW5nOi0wLjE1NHB4O2NvbG9yOmJsYWNrIj5Ob3Rpb248L2Rpdj48ZGl2IHN0eWxlPSJmb250LXN0eWxlOm5vcm1hbDtmb250LXdlaWdodDo0MDA7Zm9udC1zaXplOjEycHg7bGluZS1oZWlnaHQ6MTVweDtjb2xvcjojQUNBQkE5O3BhZGRpbmctdG9wOjNweCI-PGEgaHJlZj0iaHR0cHM6Ly93d3cubm90aW9uLnNvLz9uPWVtYWlsX2RpZ2VzdF9ob21lJm49ZW1haWxfZm9vdGVyJnB2cz0xMDMiIHN0eWxlPSJjb2xvcjppbmhlcml0Ij5Ob3Rpb24uc288L2E-LCB0aGUgY29ubmVjdGVkIHdvcmtzcGFjZTxici8-Zm9yIGRvY3MsIHByb2plY3RzLCBhbmQgd2lraXMuPC9kaXY-PC9kaXY-PC9kaXY-PGRpdj48ZGl2IHN0eWxlPSJtYXJnaW4tdG9wOjEycHgiPjxhIGhyZWY9Imh0dHBzOi8vd3d3Lm5vdGlvbi5zby9zcGFjZS84OTIyMjg4YzI3NjI0ZWQ5OGMwZDY0ODJkNjBiZTQ0ZT90YXJnZXQ9bm90aWZpY2F0aW9ucyZuPWVtYWlsX2RpZ2VzdF9ob21lJm49ZW1haWxfZm9vdGVyX2FkanVzdF9zZXR0aW5ncyZwdnM9MTAzIiBzdHlsZT0iY29sb3I6I0M0QzRDNCI-VXBkYXRlIHlvdXIgZW1haWwgc2V0dGluZ3M8L2E-PC9kaXY-PC9kaXY-PC9kaXY-PC9kaXY-CiAgICAgICAgICAgICAgPC90ZD4KICAgICAgICAgICAgPC90cj4KICAgICAgICAgIDwvdGFibGU-CiAgICAgICAgDQo8aW1nIGJvcmRlcj0iMCIgd2lkdGg9IjEiIGhlaWdodD0iMSIgYWx0PSIiIHNyYz0iaHR0cDovL3NwLm1haWwubm90aW9uLnNvL3EvMHFBNXVPVFptTUZ6SDVVZ2xKWjRMd35-L0FBUVl6Z0F-L1JnUm94YWd2UGxjRGMzQmpRZ3BtNFM4ajQyYU1fRy1UVWhGeWVXRnVRSE5vWVhKbGNHRm5aUzVwYjFnRUFBQUFBQX5-Ij4NCjwvYm9keT4KICAgICAgPC9odG1sPg0KDQo="}}})
  ;
  )

(defn get-threads [access-token]
  (let [resp (gmail-api-get access-token "/users/me/threads?includeSpamTrash=true")]
    (-> resp
        :threads)))

(defn get-threads-after-history [access-token history-id]
  (->> (get-threads access-token)
       (filter #(> (parse-long (:historyId %)) history-id))))

(defn get-largest-thread-history [threads]
  (->> threads
       (map :historyId)
       (map parse-long)
       (reduce max 0)))

(comment
  (get-largest-thread-history [{:historyId "1234"} {:historyId "12345"} {:historyId "123"}])
  ;
  )

(defn archive-and-apply-our-label [access-token thread-id label-id]
  (gmail-api-post access-token
                  (str "users/me/threads/" thread-id "/modify")
                  {:addLabelIds [label-id]
                   :removeLabelIds ["INBOX" "UNREAD"]}))

(comment
  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (archive-and-apply-our-label <access-token>
                               "192ba4e62a9be609"
                               "Label_1330615818894066579")
  ;
  )

;; POST https://gmail.googleapis.com/gmail/v1/users/{userId}/labels

(defn- find-label [access-token label-name]
  (let [{:keys [labels]} (gmail-api-get access-token
                                        "users/me/labels")]
    (->> labels
         (filter #(= (str/lower-case (:name %)) (str/lower-case label-name)))
         first)))

(comment
  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (find-label <access-token>
              "Relevance")
  ;
  )

(defn make-label [access-token label-name]
  (try
    (gmail-api-post access-token
                    "users/me/labels"
                    {:name label-name})
    (catch Exception ex
      (let [{:keys [status]} (ex-data ex)]
        (if (= status 409)
          (find-label access-token label-name)
          (throw ex))))))

(defn- get-info-to-thread
  "pulls out the fields we need to make sure this email threads correctly"
  [thread]
  (let [first-message-headers (-> thread :messages first :payload :headers)]
    {:message-id (->> first-message-headers
                      (filter #(= (:name %) "Message-ID"))
                      first
                      :value)
     :subject (->> first-message-headers
                   (filter #(= (:name %) "Subject"))
                   first
                   :value)
     :recipient (->> first-message-headers
                     (filter #(= (:name %) "From"))
                     first
                     :value)}))

;; Appropriately formatted email should look like this

;; From: <ryan@sharepage.io>
;; To: <ryan@echternacht.org>
;; References: <CAKTjL-3XuM+-01SdvJ5FDguiUF=9fSto9qHW-XSaaBaS5XwtTg@mail.gmail.com>
;; In-Reply-To: <CAKTjL-3XuM+-01SdvJ5FDguiUF=9fSto9qHW-XSaaBaS5XwtTg@mail.gmail.com>
;; Subject: Re: How to use digital transformation

;; I sent this from the API! hopefully it threads!

(defn- make-relevance-auto-reponse-email [front-end-base-url {:keys [email public-link first-name]} thread]
  (let [{:keys [recipient message-id subject]} (get-info-to-thread thread)
        body (stache/render email-body-template
                            {:front-end-base-url front-end-base-url
                             :public-link public-link
                             :first-name first-name})]
    (str "From: <" email ">\n"
         "To: " recipient "\n"
         "References: " message-id "\n"
         "In-Reply-To: " message-id "\n"
         "Subject: " subject "\n"
         "\n"
         body)))

(defn send-relevance-response [front-end-base-url access-token thread user]
  (let [email-text (make-relevance-auto-reponse-email front-end-base-url user thread)]
    (gmail-api-post access-token
                    "users/me/messages/send"
                    {:raw (base64-url-encode email-text)
                     :threadId (:id thread)})))

(comment
  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (try
    (send-relevance-response "http://app.buyersphere-local.com"
                             <access-token>
                             {:id "192ba4e62a9be609"
                              :messages [{:payload
                                          {:headers
                                           [{:name "From",
                                             :value "ryan echternacht <ryan@echternacht.org>"}
                                            {:name "Message-ID",
                                             :value "<CAKTjL-38QELTr1Fr0ddcRu4KkSyitQibE3K58LnUd=hQAZ6Pig@mail.gmail.com>"}
                                            {:name "Subject",
                                             :value "This is a test email"}]}}]}
                             {:email "ryan@relevance.to"
                              :first-name "ryan"
                              :public-link "asdf"})
    (catch Exception ex
      ex))
  ;
  )


;; Appropriately formatted email should look like this

;; From: <ryan@sharepage.io>
;; To: <ryan@echternacht.org>
;; Content-Type: text/html
;; Subject: Re: How to use digital transformation

;; I sent this from the API! hopefully it threads!

(defn- wrap-in-blockquote [text]
  (str "<blockquote class=\"gmail-blockquote\""
       "style=\"margin: 0px 0px 0px 0.8ex; border-left: 1px solid rgb(204, 204, 204); padding-left: 1ex;\">"
       text
       "</blockquote>"))

(defn- make-reply-email [email {:keys [snippet sender body]} message]
  (let [draft-body (str message "<br><br>" (wrap-in-blockquote body))]
    (str "From: <" email ">\n"
         "To: " sender "\n"
         "Subject: RE: " snippet "\n"
         "Content-Type: text/html\n"
         "\n"
         draft-body)))

(defn send-outreach-reply!
  "this function will send an email on behalf of a user. use with care"
  [access-token user outreach message]
  (let [email-text (make-reply-email (:email user) outreach message)]
    (gmail-api-post access-token
                    "users/me/messages/send"
                    {:raw (base64-url-encode email-text)})))

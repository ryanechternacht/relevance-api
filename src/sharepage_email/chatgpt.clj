(ns sharepage-email.chatgpt
  (:require [cheshire.core :as json]
            [clj-http.client :as http]))

(def ^:private api-key "sk-proj-HKUrS1PfVNHMSERStFyTT3BlbkFJ1eb3LCJ4N5fxqpGlFBJ8")

(def ^:private sales-email-prompt (slurp "resources/chatgpt-prompts/is-a-sales-email.txt"))

(defn is-this-a-sales-email?
  [email-body]
  (let [body {:model "gpt-4o"
              :messages [{:role "system"
                          :content sales-email-prompt}
                         {:role "user"
                          :content email-body}]}
        response (http/post "https://api.openai.com/v1/chat/completions"
                            {:content-type :json
                             :as :json
                             :accept :json
                             :oauth-token api-key
                             :body (json/generate-string body)})
        yes-or-no (-> response :body :choices first :message :content)]
    (= "Yes" yes-or-no)))

(comment
  (is-this-a-sales-email? "Hi Ryan,

I was just on the Swaypage website and couldn't find anything around your security and compliance posture.

There has been a significant increase in both SOC 2 and ISO27001 examinations over the past 2 years and companies are beginning to build a strong cybersecurity foundation at an earlier stage to increase brand appeal. Curious to know how Swaypage is handling security requirements from your customers today?

Here are 6 Ways That SOC 2 Compliance Gives Your Company an Edge, which is also applicable for ISO27001 certifications. 

Open to seeing where we may be able to help?

Best,
Jack

--

Jack Brozynski 
414.530.1334 | Book Time
Drata Inc. 4660 La Jolla Village Drive Ste 100 
San Diego, CA 92122")
  
  (is-this-a-sales-email? "Hi Ryan,

As I’m building up stronger linkages with the data professionals, I found you on LinkedIn as a CTO at Sharepage and I hope you would be the right person to discuss data change management solutions.

I'm the founder of Sort, an open-access tool that enables engineering best practices for your database, such as managing issues, suggesting changes (think GitHub pull requests), improving discoverability, and improving collaboration.

I’d like to emphasize that it is not about a sale, as Sort can be used at zero cost. My goal is to connect with professionals like you to learn more about the data quality and data management challenges that you face as well as share more information on how Sort can help address some of them to save time and focus on more important tasks.

How do you feel about a discovery meeting next Monday or Tuesday? Or would it be more relevant for someone from your team?

Best,
--
Jason Zucchetto
Co-Founder
Sort")
  
  (is-this-a-sales-email? "Dear patient,
For your convenience, the billing team at Ovation Fertility--the provider of your fertility laboratory services--sent this secure link to your email on file to collect payment using a credit card. Your Artisan patient portal will have a statement of services rendered or an estimate of future services that requires prepayment. If you have any questions, please call us at 513.493.0623.
Thank you for the courtesy of your prompt payment. Please note an expedited processing fee of $250 will be charged if consents and prepayments are not received 3 business days prior to the date of your procedure.
To make a payment, please click the link below:
Ovation Fertility - Invoice Payment")
  ;
  )


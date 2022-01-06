(ns elephantlaboratories.mailing
  (:require
   [clojure.string :as string]
   [elephantlaboratories.mongo :as db]))

(defn insert-sign-up
  [db params]
  (db/insert! db :mailing-list params))

(defn find-name
  [full-name]
  (let [tokens (string/split full-name #" +")
        number (count tokens)]
    (if (> number 1)
      [(string/join " " (take (dec number) tokens))
       (last tokens)]
      [(first tokens) ""])))

(defn make-line
  [sign-up]
  (let [email (get sign-up :email)
        full-name (get sign-up :name)
        [first-name last-name] (find-name full-name)]
    (string/join "," [email first-name last-name])))

(defn generate-csv
  [db]
  (let [sign-ups (db/find-all db :mailing-list)
        header "Email Address,First Name,Last Name"
        lines (map make-line sign-ups)
        lines (remove #(= ",," %) lines)]
    (string/join "\n" (conj lines header))))

(defn write-csv
  [db filename]
  (spit filename (generate-csv db)))

(def mongo-connection
  {:host "localhost"
   :port 27017
   :database "elephantlaboratories"})

(defn -main
  [& args]
  (let [db (db/connect! mongo-connection)]
    (try
      (write-csv db "dump/mailchimp-import.csv")
      (catch Exception e (.printStackTrace e)))))

(ns elephantlaboratories.prismofeverything.routes
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.util.response :as response]
   [ring.util.http-response :as http-response]
   [ring.util.http-response :refer [content-type ok]]
   [selmer.parser :as parser]
   [elephantlaboratories.config :refer [env]]))

(defn music-dir []
  (io/file (or (env :prism-music-dir) "music")))

;; Given a track name (= top-level dir name), returns the inner dir:
;;   music-dir/name/name/
(defn track-inner-dir [name]
  (io/file (music-dir) name name))

(defn render-page [_request]
  (content-type
   (ok
    (parser/render-file
     "prism.html"
     {:page         "prism.html"
      :csrf-token   ""
      :current-page "prism-home"}))
   "text/html; charset=utf-8"))

(defn parse-description [name]
  (let [f (io/file (track-inner-dir name) "description.txt")]
    (if (.exists f)
      (let [lines       (str/split-lines (slurp f))
            date        (str/trim (or (first lines) ""))
            ;; skip line 1 (date) and line 2 (blank), join the rest
            description (->> (drop 2 lines) (str/join "\n") str/trim)]
        {:date date :description description})
      {:date "" :description ""})))

(defn display-name [dir-name]
  (str/replace dir-name #"[-_]" " "))

(defn safe-name? [s]
  (and (seq s)
       (not (str/includes? s ".."))
       (not (str/includes? s "/"))
       (not (str/includes? s "\\"))))

(defn read-track [dir]
  (let [name (.getName dir)
        inner (track-inner-dir name)
        mp3   (io/file inner (str name ".mp3"))]
    (when (and (.isDirectory inner) (.exists mp3))
      (let [{:keys [date description]} (parse-description name)]
        {:name        name
         :display-name (display-name name)
         :date        date
         :description description
         :url         (str "/tracks/" name)
         :cover       (str "/covers/" name)}))))

(defn get-tracks [_request]
  (let [dir (music-dir)
        tracks (when (.exists dir)
                 (->> (.listFiles dir)
                      (filter #(.isDirectory %))
                      (keep read-track)
                      (sort-by :name)
                      vec))]
    (ok {:tracks (or tracks [])})))

(defn serve-track [request]
  (let [name (-> request :path-params :name)]
    (if (safe-name? name)
      (let [f (io/file (track-inner-dir name) (str name ".mp3"))]
        (if (.exists f)
          (-> (response/file-response (.getPath f))
              (response/content-type "audio/mpeg"))
          (http-response/not-found "Track not found")))
      (http-response/bad-request "Invalid track name"))))

(defn serve-cover [request]
  (let [name (-> request :path-params :name)]
    (if (safe-name? name)
      (let [f (io/file (track-inner-dir name) "cover.jpg")]
        (if (.exists f)
          (-> (response/file-response (.getPath f))
              (response/content-type "image/jpeg"))
          (http-response/not-found "Cover not found")))
      (http-response/bad-request "Invalid track name"))))

(defn prism-routes []
  [""
   ["/"               {:get render-page}]
   ["/api/tracks"     {:get get-tracks}]
   ["/tracks/:name"   {:get serve-track}]
   ["/covers/:name"   {:get serve-cover}]])

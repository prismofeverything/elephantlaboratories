(ns elephantlaboratories.prism
  (:require
   [reagent.core :as r]
   [ajax.core :refer [GET]]))

;; ── State ────────────────────────────────────────────────────────────────────

(defonce state
  (r/atom {:tracks        []   ; original list from API
           :sorted        []   ; currently ordered/shuffled list
           :sort-order    :newest
           :current-index nil
           :loaded        false}))

;; Holds the JS audio element so we can control it imperatively.
(defonce audio-el (atom nil))

;; ── Helpers ──────────────────────────────────────────────────────────────────

(defn sorted-tracks [tracks order]
  (case order
    :newest (vec (sort-by :date #(compare %2 %1) tracks))
    :oldest (vec (sort-by :date tracks))
    :random (vec (shuffle tracks))))

(defn current-track []
  (let [{:keys [sorted current-index]} @state]
    (when (and current-index (< current-index (count sorted)))
      (nth sorted current-index))))

;; ── Audio control ─────────────────────────────────────────────────────────────

(defn load-and-play! [index]
  (swap! state assoc :current-index index)
  (when-let [audio @audio-el]
    (let [track (nth (:sorted @state) index)]
      (set! (.-src audio) (:url track))
      (.load audio)
      (.play audio))))

(defn next-track! []
  (let [{:keys [current-index sorted]} @state]
    (when (and current-index (< (inc current-index) (count sorted)))
      (load-and-play! (inc current-index)))))

;; ── Data loading ──────────────────────────────────────────────────────────────

(defn load-tracks! []
  (GET "/api/tracks"
       {:response-format (ajax.core/json-response-format {:keywords? true})
        :handler
        (fn [data]
          (let [tracks  (mapv (fn [t]
                                {:name         (:name t)
                                 :display-name (:display-name t)
                                 :date         (:date t)
                                 :description  (:description t)
                                 :url          (:url t)
                                 :cover        (:cover t)})
                              (:tracks data))
                order   (:sort-order @state)
                sorted  (sorted-tracks tracks order)]
            (swap! state assoc
                   :tracks        tracks
                   :sorted        sorted
                   :loaded        true
                   :current-index (when (seq sorted) 0))))
        :error-handler
        (fn [_] (swap! state assoc :loaded true))}))

;; ── Sort control ──────────────────────────────────────────────────────────────

(defn set-sort! [order]
  (let [tracks (:tracks @state)
        sorted (sorted-tracks tracks order)]
    (swap! state assoc :sort-order order :sorted sorted :current-index nil)
    (when-let [audio @audio-el]
      (.pause audio)
      (set! (.-src audio) ""))))

;; ── Components ────────────────────────────────────────────────────────────────

(defn sort-button [label order]
  [:button
   {:class    (str "sort-btn" (when (= (:sort-order @state) order) " active"))
    :on-click #(set-sort! order)}
   label])

(defn now-playing-panel []
  (when-let [track (current-track)]
    [:div {:class "prism-now-playing"}
     [:img {:class "prism-cover" :src (:cover track) :alt "Cover art"}]
     [:div {:class "prism-now-playing-info"}
      [:p {:class "now-playing-label"} "Now playing"]
      [:h1 {:class "prism-title"} (:display-name track)]
      [:p  {:class "prism-date"}  (:date track)]
      [:p  {:class "prism-description"} (:description track)]]]))

(defn track-row [track index]
  (let [playing? (= index (:current-index @state))]
    [:li
     {:class    (str "track-item" (when playing? " playing"))
      :on-click #(load-and-play! index)}
     [:span {:class "track-number"}
      (if playing? "▶" (inc index))]
     [:span {:class "track-name"} (:display-name track)]
     [:span {:class "track-date"} (:date track)]]))

(defn player-bar []
  [:div {:class "prism-player"}
   [:div {:class "prism-player-inner"}
    [:audio {:id       "prism-audio"
             :controls true
             :on-ended next-track!
             :ref      (fn [el] (when el (reset! audio-el el)))}]]])

(defn prism-home-page []
  (r/create-class
   {:component-did-mount
    load-tracks!

    :reagent-render
    (fn []
      (let [{:keys [sorted loaded]} @state]
        [:div {:class "prism-app"}

         [now-playing-panel]

         [:div {:class "prism-sort"}
          [:span {:class "prism-sort-label"} "Order:"]
          [sort-button "Newest" :newest]
          [sort-button "Oldest" :oldest]
          [sort-button "Shuffle" :random]]

         (if loaded
           [:ul {:class "prism-tracks"}
            (map-indexed
             (fn [i track]
               ^{:key (:name track)}
               [track-row track i])
             sorted)]
           [:p {:class "prism-loading"} "Loading tracks…"])

         [player-bar]]))}))

(ns elephantlaboratories.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [goog.events :as events]
   [goog.history.EventType :as HistoryEventType]
   [markdown.core :refer [md->html]]
   [elephantlaboratories.ajax :as ajax]
   [ajax.core :refer [GET POST]]
   [reitit.core :as reitit]
   [clojure.string :as string])
  (:import goog.History))


(defonce session (r/atom {:page :home}))


(defn home-nav
  []
  [:nav {:class "headernav headernav--ele"}
   [:ul {:class "headernav__menu headernav__menu--ele"}
    [:li {:class "headernav__menu__item headernav__menu__item--home  headernav__menu__item--home--ele"}
     [:a {:href "/"}
      [:img {:src "/assets/images/elabs/logo_elephantlab-symbol_@4x.png", :alt "Elephant Laboratories Logo", :class "header__logo header__logo--ele", :width "53", :height "35"}]
      [:span {:class "headernav__menu__item--home__tagline headernav__menu__item--home__tagline--ele"}
       "Elephant" [:br] "Laboratories"]]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/"} "Our game" 
      [:span {:class "smallest-hide-inline"} " "]" Sol »"]]]])

(defn home-header
  []
  [:header {:class "container container--ele container--ele--header"}
   [:div {:class "constrainer"}
    [:h1 {:class "h1 h1--ele"} "We are Elephant Laboratories"]
    [:p {:class "intro intro--ele"} "Elephant Laboratories lies deep in the pulsating schism between dreams and reality; creativity and method; invention and proof. We exist to transform experience and ideas into functional art."]
    [:div {:class "half-and-half"}
     [:div {:class "half-and-half__item"}
      [:p "Founded by Ryan Spangler in 2008, Elephant Laboratories is an incubator for art, invention, joy and wonder. Our interests range from music, cellular biology, board games, electronics, books, physical expression, and beyond."]]
     [:div {:class "half-and-half__item"}
      [:p "We believe inspiration can come at any time from any source and creations come forth from that well by a complex blend of passion, focus, fun, hard work, failure, drive and patience."]]]]])

(defn home-main
  []
  [:main {:class "main-content"} 
   [:div {:class "container container--ele"}
    [:div {:class "constrainer"}
     [:div {:class "flex-vert container--ele__floating-heads"}
      [:div {:class "flex-vert__item flex-vert__item--light flex-vert__item--floatinghead"}
       [:img {:src "/assets/images/portrait_ryan_@2x.png", :alt "Portrait: Ryan Spangler", :class "image--floating-head", :width "113", :height "113"}]
       [:h2 {:class "h2"} "Ryan"]
       [:p "Musician, board game designer, software engineer, and adventurer on several planes of existence, Ryan is an outward expression of the possibility of all things. Ryan’s ability to accept all ideas as equally viable until proven otherwise affords him a boundless well of creativity upon which to draw for his creations. His insatiable curiosity propels him along an endless series of fascinations (most recently computational biology and board games) from which new idea pods burst forth with alarming alacrity. As the founder of Elephant Laboratories, Ryan is excited to bring as many of his (and his team’s) pods to life as possible."]
       [:p 
        [:a {:href "mailto:ryan@elephantlaboratories.com"} "Email Ryan »"]]]
      [:div {:class "flex-vert__item flex-vert__item--light flex-vert__item--floatinghead"}
       [:img {:src "/assets/images/portrait_sean_@2x.png", :alt "Portrait: Sean Spangler", :class "image--floating-head", :width "113", :height "113"}]
       [:h2 {:class "h2"} "Sean"]
       [:p "Sean is a wonder of nature. Part dreamer, part goat, part bee harmony, his incisive insight into the reality of design impels Elephant Laboratories along into unexplored realms. He is also Ryan’s lifelong mentor and collaborator, and (not so commonly known) is the unabashed optimist of the group. Sean keeps our spirits buoyed with his lighthearted philosophy of life and sunny outlook. Sean's third sight provides the balancing force to drive the engine of creativity at Elephant Laboratories. He is Ryan’s first, best, and always partner in invention. Also a moderate hermit, he will not answer your emails."]]
      [:div {:class "flex-vert__item flex-vert__item--light flex-vert__item--floatinghead"}
       [:img {:src "/assets/images/portrait_jodi_@2x.png", :alt "Portrait: Jodi Sweetman", :class "image--floating-head", :width "113", :height "113"}]
       [:h2 {:class "h2"} "Jodi"]
       [:p "Jodi Sweetman is a master of interpersonal subtleties and extrapersonal festivities. Jodi is the great anchor upon which the various endeavors of Elephant Laboratories make contact with reality. Trained in the wild frontier of Michigan, she learned to handle masses of unruly humans while bartending and then managing digital projects (which apparently are much the same). Her drive, intensity, vision and anticipation of all things (even things that don’t happen) lend a solid basis for the great flights of imagination the Elephant Laboratories team embark on every day."]
       [:p 
        [:a {:href "mailto:jodi@elephantlaboratories.com"} "Email Jodi »"]]]]]
    [:div {:class "constrainer"}
     [:hr]]
    [:div {:class "constrainer"}
     [:div {:class "golden"}
      [:div {:class "golden__item golden__item--a"}
       [:img {:src "/assets/images/box_hero_solo_@2x.png", :alt "Product Shot: Sol", :class "image--featured", :width "500", :height "500"}]]
      [:div {:class "golden__item golden__item--b"}
       [:h2 {:class "h2--kicker h2--kicker--ele"} "Our newest game"]
       [:h3 {:class "h2 h2--ele"} "SOL " 
        [:span {:class "h2__tagline h2__tagline--ele"} "Last Days of a Star"]]
       [:p "Sol: Last Days of a Star is a strategic game of solar destruction and salvation for 1-5 players. Play as one of the five worlds orbiting the Sun, diving into the searing plasma to harvest critical energy — energy needed to fuel your escape before the Sun goes supernova. Sol has simple, easy to learn rules, a deep decision space and emergent strategy that unfolds over " 
        [:nobr "multiple plays."]]
       [:p 
        [:a {:href "/sol/", :class "button button--ele"} "Learn More"]]]]] [:br]]])

(defn home-footer
  []
  [:footer {:class "footer footer--ele"}
   [:ul {:class "footer__menu footer__menu--ele"}
    [:li {:class "footer__menu__logo"}
     [:a {:href "/"}
      [:img {:src "/assets/images/elabs/footer_logo_elelab_@2x.png", :alt "Elephant Laboratories Logo", :class "footer__logo", :width "65", :height "45"}]]
     [:p {:class "footer__menu__logo__broughttoyou"}
      [:a {:href "/"}
       [:span {:class "broughttoyou__elabs--ele"} "Elephant Laboratories"]
       [:span {:class "broughttoyou__pdx"} "in Portland, Oregon"]]]] 
    [:li {:class "footer__menu__social"}
     [:p "Connect with us"]
     [:p {:class "footer__menu__social__icons"}
      [:a {:href "https://boardgamegeek.com/boardgame/174837/sol-last-days-star", :rel "external"}
       [:img {:src "/assets/images/elabs/footer_connect_bgg_@2x.png", :alt "BoardGameGeek", :class "icon", :width "32", :height "30"}]]
      [:a {:href "http://twitter.com/elephantnahpele", :rel "external"}
       [:img {:src "/assets/images/elabs/footer_connect_twitter_@2x.png", :alt "Twitter", :class "icon", :width "24", :height "30"}]]
      [:a {:href "http://facebook.com/ElephantLabsGames", :rel "external"}
       [:img {:src "/assets/images/elabs/footer_connect_facebook_@2x.png", :alt "Facebook", :class "icon", :width "24", :height "30"}]]
      [:a {:href "https://www.kickstarter.com/projects/elephantlaboratories/sol-last-days-of-a-star/", :rel "external"}
       [:img {:src "/assets/images/elabs/footer_connect_kickstarter_@2x.png", :alt "Kickstarter", :class "icon", :width "19", :height "30"}]]]]]
   [:div {:class "footer__copyright footer__copyright--ele"}
    [:p "© 2017 Elephant Laboratories, LLC. " 
     [:nobr "All rights reserved"]]
    [:p "Built by " 
     [:a {:href "https://www.happyfanfare.com", :rel "external"} "Happy Fanfare"]]]])

(defn home-page
  []
  [:<>
   [home-nav]
   [home-header]
   [home-main]
   [home-footer]])


(defn sol-nav
  []
  [:nav {:class "headernav"}
   [:ul {:class "headernav__menu"}
    [:li {:class "headernav__menu__item headernav__menu__item--home"}
     [:a {:href "/sol/"} "Sol " 
      [:span {:class "headernav__menu__item--home__tagline"} "Last Days of a Star"]]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/story/"} "Gameplay"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/worlds/"} "Mythos"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/background/"} "Media"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/buy/"} "Buy"]]]])

(defn sol-header
  []
  [:header {:class "container container--homeheader"}
   [:div {:class "constrainer"}
    [:div {:class "masthead measure"}
     [:h1 {:class "h1 masthead__h1"}
      [:img {:src "/assets/images/sol_logo_@2x.png", :alt "Sol", :class "masthead__image", :width "500", :height "270"}]]
     [:h2 {:class "h2 masthead__h2"} "Last Days of " 
      [:nobr "a Star"]
      [:span "From Elephant Laboratories"]]
     [:p "Sol: Last Days of a Star is a strategic game of solar destruction and salvation for 1–5 players. Play as one of the five worlds orbiting the Sun, diving into the searing plasma to harvest critical energy — energy needed to fuel your escape before the Sun goes supernova. Sol has simple, easy to learn rules, a deep decision space and emergent strategy that unfolds over " 
      [:nobr "multiple plays"]"."]
     [:p {:class "buttonwrap"}
      [:a {:href "#what", :class "button buttonwrap__button"} "Learn More"]
      [:a {:href "/sol/buy/", :class "button buttonwrap__button button--outline"} "Sign up!"]]
     [:aside {:class "current-status"}
      [:strong "CURRENT STATUS"]": PLANNING REPRINT!"]]]])

(defn sol-main
  []
  [:<>
   [:main {:class "main-content"}
    [:div {:class "container container--quote"}
     [:div {:class "constrainer"}
      [:blockquote {:class "container--quote__bq"}
       [:p "“The game brims with wonder.”"]
       [:cite 
        [:span {:class "cite__author"} "Cole Wehrle"]
        [:span {:class "cite__role"} "designer of Pax Pamir"]]]]]
    [:div {:class "container container--light", :id "what"}
     [:div {:class "constrainer"}
      [:div {:class "half-and-half"}
       [:div {:class "half-and-half__item"}
        [:img {:src "/assets/images/box_hero_solo_@2x.png", :alt "Product Shot: Sol", :class "image--featured", :width "500", :height "500"}]]
       [:div {:class "half-and-half__item"}
        [:div {:class "sol-rule sol-rule--gold"}
         [:h2 {:class "h2"} "What is Sol?"]
         [:p "Sol is a race against time, carefully balanced with a fresh engine-building mechanic uniquely rooted in mutual benefit and competition. Each game of Sol begins as an open starscape, ripe for emerging interdependent networks and careful planning at every turn: build your energy node so that other players are enticed by its strategic placement; activate an opponent’s foundry when they are low on energy so you capture the bonus; plot a multiple activation of other players’ transmit towers without ever having to build your own. There are countless approaches to explore in " 
          [:strong "Sol: Last Days " 
           [:nobr "of a Star."]]]
         [:p "Sol has very little luck, but the compounding of simple actions keep the game moving quickly. The high degree of player interaction and the array of Instability Effects allow for a surprisingly customizable vibe to " 
          [:nobr "each game."]]
         [:p 
          [:a {:href "/sol/buy/", :class "button button--outline"} "Sign up!"]]]]]
      [:h3 {:class "h3 align-center"} "Many Ways " 
       [:nobr "to Play"]]
      [:div {:class "half-and-half"}
       [:div {:class "half-and-half__item"}
        [:p "Sol’s primary play mode allows for 2–5 players, with a compelling solo play variant. Thirty instability effect cards ensure that every session is unique, and allow fine-tuning the game’s duration, degree of difficulty, and the option to add " 
         [:nobr "player-v-player conflict."]]
        [:p 
         [:a {:href "http://elephantlaboratories.com/sol-rulebook.pdf", :rel "external"}
          [:nobr "Download the rulebook »"]]]]
       [:div {:class "half-and-half__item"}
        [:p "Players may also choose to explore Sol as a co-op; choose quick start with “vestigial structures” already in place; or add “trigger event” cards for additional " 
         [:nobr "solar unpredictability."]]
        [:p 
         [:a {:href "/sol/story/"} "Learn more " 
          [:nobr "about gameplay »"]]]]]]]
    [:div {:class "container container--dark"}
     [:div {:class "constrainer"}
      [:div {:class "half-and-half"}
       [:div {:class "half-and-half__item"}
        [:h2 {:class "h2"} "What’s in " 
         [:nobr "the box?"]]
        [:ul {:class "no-bullets two-columns two-columns--narrow"}
         [:li "150 Custom plastic player pieces"]
         [:li "89 Energy cubes"]
         [:li "30 Instability effect cards"]
         [:li "96 Instability cards"]
         [:li "8 Trigger event cards"]
         [:li "7 Wooden instability tokens"]
         [:li 
          [:nobr "1 Double-sided game board"]]
         [:li "5 Player holds"]
         [:li "5 Player aids"]
         [:li "1 Momentum track"]
         [:li "1 Instability marker"]
         [:li "1 Rulebook"]
         [:li "1 Rules summary sheet"]
         [:li "1 Mythos book"]]
        [:img {:src "/assets/images/inthebox_stats_@2x.png", :alt "inthebox_stats_@2x", :width "423", :height "82"}]]
       [:div {:class "half-and-half__item half-and-half__item--image"}
        [:img {:src "/assets/images/box_inthebox_@2x.png", :alt "Product Shot: Sol", :class "image--featured"}]
        [:p {:id "chocolat_gallery", :class "gallery", :data-chocolat-title "What’s in the box?"} "View Images" 
         [:span {:class "gallery__menu"}
          [:a {:class "button button--gallery chocolat-image", :href "/assets/images/sol-gallery_1.jpg", :title "Image 1 Caption"} "1"]
          [:a {:class "button button--gallery chocolat-image", :href "/assets/images/sol-gallery_2.jpg", :title "Image 2 Caption"} "2"]
          [:a {:class "button button--gallery chocolat-image", :href "/assets/images/sol-gallery_3.jpg", :title "Image 3 Caption"} "3"]
          [:a {:class "button button--gallery chocolat-image", :href "/assets/images/sol-gallery_4.jpg", :title "Image 4 Caption"} "4"]
          [:a {:class "button button--gallery chocolat-image", :href "/assets/images/sol-gallery_5.jpg", :title "Image 5 Caption"} "5"]]]]]]]
    [:div {:class "container container--worldsofsolhome"}
     [:div {:class "constrainer"}
      [:div {:class "half-and-half"}
       [:div {:class "half-and-half__item"}
        [:div {:class "sol-rule sol-rule--white"}
         [:h2 {:class "h2"} "The Worlds " 
          [:nobr "of Sol"]]
         [:p "The story behind Sol is a richly-textured utopian future, a time of limitless resources in which war, poverty, and suffering are distant memories…until the sun begins to die. The 32-page Mythos Book, beautifully written by CJ Hallowell, provides a window into the vibrant cultures competing to preserve their way of life in the face of impending " 
          [:nobr "solar annihilation."]]
         [:p 
          [:a {:href "/sol/worlds/"} "Learn more " 
           [:nobr "about Sol Mythos »"]]]]
        [:div {:class "sol-rule sol-rule--white"}
         [:h2 {:class "h2"} "Videos " 
          [:nobr "and Links"]]
         [:p "Watch how-to’s, play-throughs and interviews with Sol’s design team. Download the complete rulebook. Read all the nice things others are saying " 
          [:nobr "about Sol."]]
         [:p 
          [:a {:href "/sol/background/"}
           [:nobr "Check out videos and links »"]]]]]
       [:div {:class "half-and-half__item half-and-half__item--light container--worldsofsol__floating-heads"}
        [:img {:src "/assets/images/portrait_ryan-sean_@2x.png", :alt "portrait_ryan-sean_@2x", :class "image--floating-head", :width "113", :height "113"}]
        [:h2 {:class "h2"} "Brothers " 
         [:nobr "in Space"]]
        [:p "Sol is the creation of brothers Ryan Spangler and Sean Spangler, whose earliest memories involve playing—and making—board games together. Their shared passion for strategy, invention, collaboration, and the act of bringing something new into the world drove them to design a game that they continue to enjoy playing…years after " 
         [:nobr "its inception."]]
        [:p "Sol is by all definitions an independent game, but it owes its existence to a vast supportive community of family, friends, and " 
         [:nobr "enthusiastic backers."]]
        [:p 
         [:a {:href "/sol/thanks/"} "We have so many " 
          [:nobr "to thank »"]]]]]]]]
   [:script
    "  $(function(){
    $('#chocolat_gallery').Chocolat({
      loop      : true,
      imageSize : 'contain',
      enableZoom  : false,
      fullScreen  : false,
    });
  });
"]])

(defn sol-footer
  []
  [:footer {:class "footer"}
   [:ul {:class "footer__menu"}
    [:li {:class "footer__menu__logo"}
     [:a {:href "/"}
      [:img {:src "/assets/images/footer_logo_elelab_@2x.png", :alt "Elephant Laboratories Logo", :class "footer__logo", :width "65", :height "45"}]]
     [:p {:class "footer__menu__logo__broughttoyou"}
      [:a {:href "/"}
       [:span {:class "broughttoyou__pre"} "Brought to you by"]
       [:span {:class "broughttoyou__elabs"} "Elephant Laboratories"]
       [:span {:class "broughttoyou__pdx"} "in Portland, Oregon"]]]]
    [:li {:class "footer__menu__social"}
     [:p "Connect with us"]
     [:p {:class "footer__menu__social__icons"}
      [:a {:href "https://boardgamegeek.com/boardgame/174837/sol-last-days-star", :rel "external"}
       [:img {:src "/assets/images/footer_connect_bgg_@2x.png", :alt "BoardGameGeek", :class "icon", :width "32", :height "30"}]]
      [:a {:href "http://twitter.com/elephantnahpele", :rel "external"}
       [:img {:src "/assets/images/footer_connect_twitter_@2x.png", :alt "Twitter", :class "icon", :width "24", :height "30"}]]
      [:a {:href "http://facebook.com/ElephantLabsGames", :rel "external"}
       [:img {:src "/assets/images/footer_connect_facebook_@2x.png", :alt "Facebook", :class "icon", :width "24", :height "30"}]]
      [:a {:href "https://www.kickstarter.com/projects/elephantlaboratories/sol-last-days-of-a-star/", :rel "external"}
       [:img {:src "/assets/images/footer_connect_kickstarter_@2x.png", :alt "Kickstarter", :class "icon", :width "19", :height "30"}]]]]]
   [:div {:class "footer__copyright"}
    [:p "© 2017 Elephant Laboratories, LLC. " 
     [:nobr "All rights reserved. | " 
      [:a {:href "/"} "Contact Us"]" | " 
      [:a {:href "/sol/thanks/"} "Game Credits"]]]
    [:p "Built by " 
     [:a {:href "https://www.happyfanfare.com", :rel "external"} "Happy Fanfare"]]]])


(defn sol-home-page
  []
  [:<>
   [sol-nav]
   [sol-header]
   [sol-main]
   [sol-footer]])


(def pages
  {:home #'home-page
   :sol-home #'sol-home-page})

(defn page
  []
  [(get pages (keyword js/currentPage))])


;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :home]
    ["/sol" :sol]]))

(defn match-route [uri]
  (->> (or (not-empty (string/replace uri #"^.*#" "")) "/")
       (reitit/match-by-path router)
       :data
       :name))
;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [^js/Event.token event]
       (swap! session assoc :page (match-route (.-token event)))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
;; (defn fetch-docs! []
;;   (GET "/docs" {:handler #(swap! session assoc :docs %)}))

(defn mount-components []
  (rdom/render [#'page] (.getElementById js/document "content")))

(defn init! []
  (ajax/load-interceptors!)
  (hook-browser-navigation!)
  (mount-components))

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

(def shopify-page "https://b5e521-7f.myshopify.com/")

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
     [:a {:href "/sol"} "Our game" 
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
        [:a {:href "/sol", :class "button button--ele"} "Learn More"]]]]] [:br]]])

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

(defn sol-nav
  []
  [:nav {:class "headernav"}
   [:ul {:class "headernav__menu"}
    [:li {:class "headernav__menu__item headernav__menu__item--home"}
     [:a {:href "/sol"} "Sol " 
      [:span {:class "headernav__menu__item--home__tagline"} "Last Days of a Star"]]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/story"} "Gameplay"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/worlds"} "Mythos"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/background"} "Media"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href shopify-page :target "none"} "Buy"]]]])

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
      [:a {:href shopify-page :target "none" :class "button buttonwrap__button button--outline"} "BUY"]
      [:a {:href "/sol/sign-up", :class "button buttonwrap__button"} "Sign up!"]]
     [:aside {:class "current-status"}
      [:strong "CURRENT STATUS"]
      ": Finalizing fulfillment for Sol: Last Days of a Star!"
      " - retail orders opening soon"]]]])

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
          [:a {:href "/sol/sign-up", :class "button button--outline"} "Sign up!"]]]]]
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
         [:a {:href "/sol/story"} "Learn more " 
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
          [:a {:href "/sol/worlds"} "Learn more " 
           [:nobr "about Sol Mythos »"]]]]
        [:div {:class "sol-rule sol-rule--white"}
         [:h2 {:class "h2"} "Videos " 
          [:nobr "and Links"]]
         [:p "Watch how-to’s, play-throughs and interviews with Sol’s design team. Download the complete rulebook. Read all the nice things others are saying " 
          [:nobr "about Sol."]]
         [:p 
          [:a {:href "/sol/background"}
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
         [:a {:href "/sol/thanks"} "We have so many " 
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
      [:a {:href "/sol/thanks"} "Game Credits"]]]
    [:p "Built by " 
     [:a {:href "https://www.happyfanfare.com", :rel "external"} "Happy Fanfare"]]]])


(defn sol-story
  []
  [:<>
   [:header {:class "container container--pageheader container--pageheader--slv2"} 
    [:div {:class "constrainer"} 
     [:h1 {:class "h1"} "GAMEPLAY"]]]
   [:main {:class "main-content"} 
    [:div {:class "container container--celestial"} 
     [:div {:class "constrainer"} 
      [:div {:class "half-and-half"} 
       [:div {:class "half-and-half__item"} 
        [:div {:class "embed-container"} 
         [:iframe {:src "https://www.youtube.com/embed/RNskvQCEICM?rel=0&controls=0&showinfo=0?ecver=2", :allowfullscreen true, :width "640", :height "360", :frameborder "0"}]]]
       [:div {:class "half-and-half__item"} 
        [:div {:class "sol-rule sol-rule--white"} 
         [:h2 {:class "h2"} "A Simple Objective"]
         [:p "Gain momentum by transmitting energy to your ark and/or hurling sundivers into the heart of the Sun. The ark with the most momentum at the end survives! (The others perish in a fiery demise.)"]
         [:p "The strategies you employ to accomplish this objective are myriad. "]
         [:p 
          [:a {:href "http://elephantlaboratories.com/sol-rulebook.pdf", :rel "external"}
           [:nobr "Download the rulebook »"]]]
         [:p 
          [:small "Please note: The game in the video features prototype elements, not final production components."]]]]]]]
    [:div {:class "container container--quote container--quote--blk2"} 
     [:div {:class "constrainer"} 
      [:blockquote {:class "container--quote__bq"} 
       [:p "“I always say there’s nothing new in board games, but the way Sol plays makes me feel like some core mechanics are so fresh that I’ve never seen them before — and Sol’s game play is not just new; it’s intriguing and exciting.”"]
       [:cite 
        [:span {:class "cite__author"} "Anthony J Gallela"]
        [:span {:class "cite__role"} "Game Developer and Chair of the KublaCon Game Design Contest"]]]]]
    [:div {:class "container container--reverse"} 
     [:div {:class "constrainer"} 
      [:div {:class "measure"} 
       [:h2 {:class "h2"} "Basic Gameplay"]
       [:p "Each player has a mothership in orbit which is where they will launch sundivers onto the board.  The motherships follow an orbital track around the perimeter of the game board, moving one space each turn, so your sundivers will emerge in different places on the board throughout the game."]
       [:h3 {:class "h3"} "Move, Convert, or Activate"]
       [:p "On your turn you have three actions to choose from: "]
       [:p 
        [:strong "Move"]" — Launch sundivers from your mothership onto the game board, fly them around the board, use them for the other two actions, and hurl them into the heart of the Sun."]
       [:p 
        [:strong "Convert"]" — Once your sundivers are in certain patterns, you can convert them into gates or stations (nodes, foundries, or towers)."]
       [:p 
        [:strong "Activate"]" — Sundivers on stations can activate those stations to harvest energy, spend energy to build new sundivers or transmit energy back to your ark."]
       [:h3 {:class "h3"} "Instability Cards"]
       [:p "You draw instability cards every time you hurl sundivers and every time you convert or activate within the three layers of the Sun. Instability cards serve two purposes:"]
       [:ol 
        [:li "You can keep one of those cards to play on a future turn–its effect is determined by which instability effect card (chosen from thirty options) contains its suit token. "]
        [:li "Of the 4–7 suits mixed into the deck, one is always the solar flare suit. Every time a solar flare is drawn, you advance the time tracker. They are shuffled randomly into the deck and on the 13th solar flare the Sun goes supernova and the game is over!"]]]]]
    [:div {:class "container container--bright", :id "replayable"} 
     [:div {:class "constrainer"} 
      [:div {:class "measure"} 
       [:h2 {:class "h2"} "Unique and Infinitely Replayable"]
       [:h3 {:class "h3"} "Orbiting Motherships"]
       [:p "Each player has a mothership on an orbital track around the perimeter of the board, moving one space each turn. Your Mothership is your base of operations and where your sundivers launch onto the board."]
       [:p "Because the motherships are always moving, you will drift farther away from your initial developments and draw closer to the those which other players have built, encouraging everyone to plan carefully for optimal use of launches, conversions, and activations. These cycles of timing provide endless variety to the gameplay."]
       [:h3 {:class "h3"} "Instability Effects"]
       [:p "Before every game a mix of instability effects are chosen which change the feel and potential of the game. Each effect is a rule or ability that enhances what players are able to do on their turn — things like teleporting sundivers or moving stations across the board. There is even a handful of player conflict effects (completely optional), for those who enjoy a bit of “take that!” in their game nights."]
       [:p "With only 4–7 effects used in each game, there is a wide array of different approaches and potential strategies to discover and explore. And because you assign each effect to a specific suit at the beginning of every game, the endless combinations make every game unique!"]]]]
    [:div {:class "container container--deep"} 
     [:div {:class "constrainer"} 
      [:div {:class "measure"} 
       [:h2 {:class "h2"} "Gameplay Variations"]
       [:p "Sol features a surprisingly flexible yet solid core mechanic that supports many variations:"]
       [:h3 {:class "h3"} "Variable game duration"]
       [:p "Both game board set up and deck creation allow for players to choose from quick play (~30-40min), standard (~60-90min), or extended play (~120-180min)."]
       [:h3 {:class "h3"} "Solo play"]
       [:p "Only one rule change for time tracking and a selection of six starting scenarios to choose from. Win conditions are defined, but high scores are yet to be known…"]
       [:h3 {:class "h3"} "Co-op"]
       [:p "Two rules changes to the base game and seven starting scenarios to choose from. May no player be left behind!"]
       [:h3 {:class "h3"} "Trigger Events"]
       [:p "Used to represent structural shifts or solar phenomena brought about by the instability of the Sun, these five additional cards can be added to the instability deck to bring a higher-degree of unpredictability, challenge and/or, opportunity to the game."]
       [:h3 {:class "h3"} "Levels of survival"]
       [:p "If someone in your game group flinches at the idea of entire worlds perishing in a fiery cataclysm, feel free to introduce the seven levels of survival for a softer, gentler Sol: Last Days of a Star."]]]]]])


(defn sol-worlds
  []
  [:<>
   [:header {:class "container container--pageheader container--pageheader--blu1"} 
    [:div {:class "constrainer"} 
     [:h1 {:class "h1"} "MYTHOS"]]]
   [:main {:class "main-content"} 
    [:div {:class "container container--celestial"} 
     [:div {:class "constrainer"} 
      [:h2 {:class "h2"} "The Worlds " 
       [:nobr "of Sol"]]
      [:p "For centuries, we lived in a Utopia, drawing energy directly from the sun. Our forebears had constructed a vast solar harvesting infrastructure to feed limitless energy to the five worlds, providing everything we could need " 
       [:nobr "or imagine."]]
      [:p "In recent years, however, massive solar flares and unprecedented instability have destroyed the solar lattice we had long taken for granted. Our planets are cast into darkness, our societies into chaos. The heliologists tell us the decline is irreversible—the instability will only increase, and any day our star could go supernova, eradicating all life in the solar system. For the apocalyptic among us, the End Times " 
       [:nobr "have come."]]
      [:p "And yet many of us still dare to hope! Each of our planets has built an Ark, poised on the edge of the solar system. While most of us will inevitably perish — These Arks wait poised at the edge of the solar system, ready to escape, but they require a great deal of energy to attain the momentum needed to flee our solar system. It was a stark realization: the only source of such vast energy is from the dying " 
       [:nobr "star itself."]]
      [:p "The urgent task we now face is to rebuild just enough of the shattered solar harvesting infrastructure to harness the energy needed to propel our Arks out of the solar system—before the Sun goes supernova and consumes everything " 
       [:nobr "we know."]]
      [:p 
       [:strong "This is our " 
        [:nobr "only hope."]]]]]
    [:div {:class "container container--worldsofsol container--worldsofsol--hawkini"} 
     [:div {:class "constrainer constrainer--worldsofsol"} 
      [:div {:class "worldsofsol__planet"} 
       [:img {:src "/assets/images/mythos_planet_hawkini_@2x.png", :alt "World of Hawkini", :width "239", :height "202"}]]
      [:div {:class "worldsofsol__desc"} 
       [:h2 {:class "h2"} "The Hawkini Federation"]
       [:p "This is the classic Utopia. Every human desire satisfied and accounted for. Food is plentiful and delicious. Disease and pain are unknown. Everyone is free to create and indulge. Everyone is a musician, everyone knows math, everyone paints and sculpts and gardens and writes poetry. They speak in poetry. Their architecture is spires and towers and suspension bridges and domes. They all have tasteful bioenhancements: multiple eyes, extra arms, tiger stripes, wings. Everything is self-expression and individualistic. Everyone is beautiful and refined and amusing " 
        [:nobr "and wise."]]]]]
    [:div {:class "container container--worldsofsol container--worldsofsol--danihelios"} 
     [:div {:class "constrainer constrainer--worldsofsol"} 
      [:div {:class "worldsofsol__planet"} 
       [:img {:src "/assets/images/mythos_planet_danihelios_@2x.png", :alt "World of Dani Helios", :width "239", :height "202"}]]
      [:div {:class "worldsofsol__desc"} 
       [:h2 {:class "h2"} "Danihelios"]
       [:p "Collective intelligence and biological enmeshing. Everyone&#39;s minds are linked, they think with one planet spanning awareness that is rich and diverse and tendriled. They have merged with plants in an inseparable way, they feel the energy from the sun, they drink light, and spill forth in every conceivable pattern of foliage and growth. Leaves and pods are spilling all over the planet. It is one giant throbbing, breathing, growing, unfurling consciousness, ecstatic and joyous, seeing with every eye at once. A " 
        [:nobr "collectivist paradise."]]]]]
    [:div {:class "container container--worldsofsol container--worldsofsol--arel"} 
     [:div {:class "constrainer constrainer--worldsofsol"} 
      [:div {:class "worldsofsol__planet"} 
       [:img {:src "/assets/images/mythos_planet_arel_@2x.png", :alt "World of Arel", :width "239", :height "202"}]]
      [:div {:class "worldsofsol__desc"} 
       [:h2 {:class "h2"} "Arel"]
       [:p "Underwater playfulness and symmetry. They swim in pods, glide through the water, feast on schools of fish and spores, play in coral and kelp beds. Their architecture is vast and unknowable, with countless underwater caverns and bubbles of structure. They are immersed in the flow and the pressure of a massive water planet, and everything is huge in scale, both spatial and temporal. They communicate through emitting noises which recreate images in the water, basically transmitting images directly from mind to mind. They have elaborate and millenia developed hierarchy which is impenetrable " 
        [:nobr "to outsiders."]]]]]
    [:div {:class "container container--worldsofsol container--worldsofsol--zyuuclarum"} 
     [:div {:class "constrainer constrainer--worldsofsol"} 
      [:div {:class "worldsofsol__planet"} 
       [:img {:src "/assets/images/mythos_planet_zyuu_@2x.png", :alt "World of Zyuu Clarum", :width "239", :height "202"}]]
      [:div {:class "worldsofsol__desc"} 
       [:h2 {:class "h2"} "Zyuu Clarum"]
       [:p "Transcendent spiritualists. The people of this world have almost entirely abandoned the material world, except for whatever vestige is necessary to support their spiritual life. They remain floating and meditating for weeks on end. Their world is closest to the sun, so they spend most of their time drawing nourishment directly from the energy streams they channel to other worlds. They are in touch with a realm beyond the one that can be measured or detected by other beings, communing directly with the strata and fundamental nature of " 
        [:nobr "the universe."]]]]]
    [:div {:class "container container--worldsofsol container--worldsofsol--sideralis"} 
     [:div {:class "constrainer constrainer--worldsofsol"} 
      [:div {:class "worldsofsol__planet"} 
       [:img {:src "/assets/images/mythos_planet_sideralis_@2x.png", :alt "World of Sideralis", :width "239", :height "202"}]]
      [:div {:class "worldsofsol__desc"} 
       [:h2 {:class "h2"} "Sideralis"]
       [:p "These people vanished long ago and were thought to be entirely extinct, but really they retreated to the darkness of the asteroid belt and have been living in open space. Not much is known about them except that their form has become nebulous and appear only as a kind of shadowy flame. They draw energy directly from open space and also consume asteroids slowly over many centuries, leaving behind only a porous and empty shell. They communicate through flashes of energy that turn the empty space into a glittering fabric of light. Their appearance in the effort to escape the supernova has surprised everyone and lead to much fear and alarm among the " 
        [:nobr "other worlds."]]]]]]])


(defn sol-background
  []
  [:header {:class "container container--pageheader container--pageheader--blk1"} 
   [:div {:class "constrainer"} 
    [:h1 {:class "h1"} "{{title}}"]]]
  [:main {:class "main-content"} 
   [:div {:class "container container--celestial"} 
    [:div {:class "constrainer"} 
     [:div {:class "half-and-half"} 
      [:div {:class "half-and-half__item"} 
       [:div {:class "embed-container"} 
        [:iframe {:src "https://www.youtube.com/embed/videoseries?list=PLgF0nUsOkxJHoUJOrYWnvAEY_7J3GqQhf&controls=0&showinfo=0?ecver=2", :allowfullscreen true, :width "640", :height "360", :frameborder "0"}]]]
      [:div {:class "half-and-half__item"} 
       [:div {:class "sol-rule sol-rule--white"} 
        [:h2 {:class "h2"} "Download & Watch"]
        [:ul 
         [:li 
          [:strong "Complete rulebook"]" — " 
          [:a {:href "http://elephantlaboratories.com/sol-rulebook.pdf", :rel "external"}
           [:nobr "Download »"]]]
         [:li 
          [:strong "SXSW Award Ceremony"]" — " 
          [:a {:href "https://www.twitch.tv/videos/239940204?t=01h14m58s", :rel "external"}
           [:nobr "Watch »"]]]
         [:li 
          [:strong "Meet the designers"]" — " 
          [:a {:href "https://www.youtube.com/watch?v=RpqhQTGowBw", :rel "external"}
           [:nobr "Watch »"]]]
         [:li 
          [:strong "Interview with James Hudson"]" — " 
          [:a {:href "https://www.youtube.com/watch?v=XVjTzr1YEHc", :rel "external"}
           [:nobr "Watch »"]]]
         [:li 
          [:strong "How to play"]" — " 
          [:a {:href "https://www.youtube.com/watch?v=RNskvQCEICM", :rel "external"}
           [:nobr "Watch »"]]]
         [:li 
          [:strong "Story animation"]" — " 
          [:a {:href "https://www.youtube.com/watch?v=kTblTZSS52Y", :rel "external"}
           [:nobr "Watch »"]]]]]]]]]
   [:div {:class "container container--quote container--quote--pur1"} 
    [:div {:class "constrainer"} 
     [:blockquote {:class "container--quote__bq"} 
      [:p "“Deceptively deep and " 
       [:nobr "deceptively complex."]"”"]
      [:cite 
       [:span {:class "cite__author"} "Lance Myxter"]
       [:span {:class "cite__role"} "Undead Viking"]]]]]
   [:div {:class "container container--reverse"} 
    [:div {:class "constrainer"} 
     [:div {:class "half-and-half"} 
      [:div {:class "half-and-half__item"} 
       [:div {:class "sol-rule sol-rule--white"} 
        [:h2 {:class "h2"} "Written Reviews"]
        [:ul {:class "no-bullets"}
         [:li 
          [:strong "Cole Wehrle"]" — " 
          [:a {:href "https://boardgamegeek.com/thread/1572071/making-space-and-time", :rel "external"}
           [:nobr "Review »"]]]
         [:li 
          [:strong "Dice Hate Me"]" — " 
          [:a {:href "http://dicehateme.com/2018/01/dice-hate-me-game-of-the-year-awards-2017/", :rel "external"}
           [:nobr "Game of the Year! »"]]]
         [:li 
          [:strong "Whats Eric Playing?"]" — " 
          [:a {:href "https://whatsericplaying.com/2018/01/15/sol-last-days-of-a-star/", :rel "external"}
           [:nobr "Review »"]]]
         [:li 
          [:strong "Jonathan H. Liu"]" — " 
          [:a {:href "https://geekdad.com/2016/05/sol/", :rel "external"}
           [:nobr "GeekDad »"]]]
         [:li 
          [:strong "Daily Worker Placement"]" — " 
          [:a {:href "http://dailyworkerplacement.com/2017/10/25/sol-last-days-of-a-star/", :rel "external"}
           [:nobr "Review »"]]]]]]
      [:div {:class "half-and-half__item"} 
       [:div {:class "sol-rule sol-rule--white"} 
        [:h2 {:class "h2"} "Video Reviews"]
        [:ul {:class "no-bullets"}
         [:li 
          [:strong "Universal Head"]" — " 
          [:a {:href "https://www.orderofgamers.com/the-joy-of-unboxing-sol-last-days-of-a-star/", :rel "external"}
           [:nobr "The Joy of Unboxing »"]]]
         [:li 
          [:strong "Heavy Cardboard"]" — " 
          [:a {:href "https://youtu.be/baELdIlBX1E", :rel "external"}
           [:nobr "Live Playthrough »"]]]
         [:li 
          [:strong "Bell of Lost Souls"]" — " 
          [:a {:href "https://youtu.be/p6jvXqZ92j4", :rel "external"}
           [:nobr "Unboxing and Preview »"]]]
         [:li 
          [:strong "Undead Viking"]" — " 
          [:a {:href "https://www.youtube.com/watch?v=lHkFxrBmzLc", :rel "external"}
           [:nobr "Video Review »"]]]]]]]]]
   [:div {:class "container container--deep"} 
    [:div {:class "constrainer"} 
     [:div {:class "flex-vert"} 
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“I always say there’s nothing new in board games, but the way Sol plays makes me feel like some core mechanics are so fresh that I've never seen them before—and Sol's game play is not just new; it's intriguing " 
        [:nobr "and exciting.”"]]
       [:cite 
        [:span {:class "cite__author"} "Anthony J Gallela"]
        [:span {:class "cite__role"} "Game Developer and Chair of the KublaCon Game Design Contest"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“Sol 
      […]feels like a major invention both mechanically and thematically. Its rules are simple and stripped down—almost to the point of being an abstract game—and yet, it sports a robust decision matrix rife with " 
        [:nobr "interesting choices.”"]]
       [:cite 
        [:span {:class "cite__author"} "Cole Wehrle"]
        [:span {:class "cite__role"} "designer of Pax Pamir"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“Sol: Last Days of a Star effortlessly blends theme with mechanics, providing a rich and unique play experience. This game is good on so many levels but what really makes it shine is the dynamic player interaction driven by the shared use of space stations, warp bridges and solar harvesters. No player can win on their own, but only one will succeed in escaping " 
        [:nobr "solar annihilation.”"]]
       [:cite 
        [:span {:class "cite__author"} "Tim Eisner"]
        [:span {:class "cite__role"} "Weird City Games, Designer of March of the Ants"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“Intense and nail-biting to the " 
        [:nobr "very end.”"]]
       [:cite 
        [:span {:class "cite__author"} "Andrew Tullsen"]
        [:span {:class "cite__role"} "Founder of Print and Play Productions"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“Deceptively deep and " 
        [:nobr "deceptively complex.”"]]
       [:cite 
        [:span {:class "cite__author"} "Lance Myxter"]
        [:span {:class "cite__role"} "Undead Viking"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“Sol is great! You are constantly trying to plan the perfect set of moves while balancing that with an " 
        [:nobr "ever-changing board.”"]]
       [:cite 
        [:span {:class "cite__author"} "Chase Van Epps"]
        [:span {:class "cite__role"} "Print and Play Productions"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“The beauty of Sol is that the players build the board together, opening new strategies each game. I keep coming back to try out " 
        [:nobr "new tricks.”"]]
       [:cite 
        [:span {:class "cite__author"} "Mohammad Ali"]
        [:span {:class "cite__role"} "Member of Stumptown Gamecrafters"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“Sol has some real meat on its bones and un underlying elegance that made it a standout at BGG.con. I can’t wait to get this to the " 
        [:nobr "table again!”"]]
       [:cite 
        [:span {:class "cite__author"} "Mischa D. Krilov"]
        [:span {:class "cite__role"} "Board Game Hero"]]]
      [:blockquote {:class "flex-vert__item flex-vert__item--bright"} 
       [:p "“I have literally played Sol dozens of times, and quite frankly it never gets old. The variability in the rule set, built on a solid base, allows me as a game master to adapt the game to the group that " 
        [:nobr "is playing”"]]
       [:cite 
        [:span {:class "cite__author"} "Chad Nichols"]
        [:span {:class "cite__role"} "Dungeon Master at Geekline 415"]]]]]]])


(defonce form
  (r/atom
   {:name ""
    :email ""
    :campaign false
    :reprint false
    :organism false}))

(defn sol-buy
  []
  [:<>
   [:header {:class "container container--pageheader container--pageheader--order"}
    [:div {:class "constrainer"}
     [:h1 {:class "h1"} "SOL reprint"]]]
   [:main {:class "main-content"}
    [:div {:class "container container--light"}
     [:div {:id "buy-sol", :class "buy"}
      [:div {:class "constrainer"}
       [:div {:class "measure"}
        [:h2 {:class "h2"} "Want to be on the mothership?"]
        [:p "Thanks for visiting! We recently had a successful campaign for the reprint of Sol: Last Days of a Star!" 
         [:br]
         "Now we are working through manufacture, as well as preparing a KS campaign for our new game, ORGANISM. "]
        [:p "If you want to receive updates about our progress, please sign up for the mailing list below."]
        [:p {:style {:color "#a19364"}} "(We will only use your information to send you occasional updates on our games. " 
         [:br]
         " You can opt out at any time. We will never sell or share your information.)"]
        [:p "Questions? You can contact us directly at " 
         [:a {:href "mailto:mothership@elephantlaboratories.com"} "mothership@elephantlaboratories.com"]]
        [:div {:class "half-and-half half-and-half--buy"}
         [:fieldset {:id "shipping-address", :class "half-and-half__item buy__fieldset"}
          [:legend {:class "h3"} "Sign up for updates!"]
          [:div {:class "buy__inputgroup"}
           [:label {:class "buy__inputgroup__label"}
            [:span {:class "buy__inputgroup__label__desc"} "Name"]
            [:input
             {:id "name"
              :name "name"
              :class "field buy__inputgroup__input--text"
              :value (:name @form)
              :on-change
              (fn [event]
                (let [value (-> event .-target .-value)]
                  (swap! form assoc :name value)))}]]]
          [:div {:class "buy__inputgroup"}
           [:label {:class "buy__inputgroup__label"}
            [:span {:class "buy__inputgroup__label__desc"} "Email"]
            [:input
             {:id "email"
              :name "email"
              :class "field buy__inputgroup__input--text"
              :value (:email @form)
              :on-change
              (fn [event]
                (let [value (-> event .-target .-value)]
                  (swap! form assoc :email value)))}]]]]]
        [:p "We would love your input as we decide how to proceed with these two games:"]
        [:div {:class "half-and-half half-and-half--buy"}
         [:fieldset {:id "feedback", :class "half-and-half__item buy__fieldset"}
          [:legend {:class "h3"} "interest"]
          [:div {:class "buy__inputgroup"}
           [:label {:class "buy__inputgroup__label"}
            [:input
             {:type "checkbox",
              :id "reprint",
              :name "reprint",
              :class "field buy__inputgroup__input--checkbox"
              ;; :value "reprint"
              :checked (:reprint @form)
              :on-change
              (fn [event]
                (let [checked (-> event .-target .-checked)]
                  (swap! form assoc :reprint checked)))}]
            [:span {:class "buy__inputgroup__label__check"} "Sol: Journey Between Worlds"]]]
          [:div {:class "buy__inputgroup"}
           [:label {:class "buy__inputgroup__label"}
            [:input
             {:type "checkbox"
              :id "organism"
              :name "organism"
              :class "field buy__inputgroup__input--checkbox"
              :value "organism"
              :checked (:organism @form)
              :on-change
              (fn [event]
                (let [checked (-> event .-target .-checked)]
                  (swap! form assoc :organism checked)))}]
            [:span {:class "buy__inputgroup__label__check"} "ORGANISM"]]]
          [:div {:class "buy__inputgroup"}
           [:label {:class "buy__inputgroup__label"}
            [:input
             {:type "checkbox"
              :id "campaign"
              :name "campaign"
              :class "field buy__inputgroup__input--checkbox"
              ;; :value "campaign"
              :checked (:campaign @form)
              :on-change
              (fn [event]
                (let [checked (-> event .-target .-checked)]
                  (swap! form assoc :campaign checked)))}]
            [:span {:class "buy__inputgroup__label__check"} "Beam of Light"]]]
          [:div {:class "buy__inputgroup"}
           [:label {:class "buy__inputgroup__label"}
            [:span {:class "buy__inputgroup__label__desc"} "Any other comments?"]
            [:textarea
             {:id "comments"
              :name "comments"
              :class "field buy__inputgroup__input--textarea"
              :value (:comments @form)
              :on-change
              (fn [event]
                (let [value (-> event .-target .-value)]
                  (swap! form assoc :comments value)))}]]]
          [:button
           {:type "submit",
            :class "button button--left"
            :on-click
            (fn [event]
              (ajax/post-mailing-list! @form))}
           "Sign up!"]]]]]]]]])

     ;; [:div {:class "outcome"}
     ;;  [:div {:class "constrainer"}
     ;;   [:div {:id "error", :class "button--right"}
     ;;    [:h2 {:class "h2 error"} "Hmmm."]
     ;;    [:span {:id "error-message", :class "buy__inputgroup__label__desc error"} "I’m sorry, that didn’t work out. Have you filled out all the fields above?"]]
     ;;   [:div {:id "success"}
     ;;    [:h2 {:class "h2"} "Thank You"]
     ;;    [:span {:id "thank-you"}
     ;;     [:p "Thank you for your purchase, " 
     ;;      [:span {:id "thank-you-name"} "friend"]"! We will be sending you an email confirmation about your order. " 
     ;;      [:br]
     ;;      [:br]"Please feel free to contact us at " 
     ;;      [:a {:href "mailto:mothership@elephantlaboratories.com?subject=Sol%20Website%20Inquiry"} "mothership@elephantlaboratories.com"]" with " 
     ;;      [:nobr "any questions."]]]]]]

(defn sol-thanks
  []
  [:<>
   [:header {:class "container container--pageheader container--pageheader--blk2"} 
    [:div {:class "constrainer"} 
     [:h1 {:class "h1"} "THANKS"]]]
   [:main {:class "main-content"} 
    [:div {:class "container container--light"} 
     [:div {:class "constrainer measure"} 
      [:h3 {:class "h3"} "In the beginning, there was my brother Sean " 
       [:nobr "and me."]]
      [:p "From my earliest memory, he was there and we were creating things. Everything else flows from this simple truth. Years later, Jodi Sweetman and I were walking through the woods. “I’ve always wanted to make a board game with my brother,” I said, shafts of light streaming through the leaves. “What would it be?” she asked, already plotting. “I don’t really know, but the board would be the Sun.”"]
      [:p "I went to see my brother that weekend and we started right away. From there, we were off. It was inspiring to rediscover that joy of creating together and to realize how fundamental it is to our being. So my first thank you is to Sean Spangler, you made me who I am. Now at least one of our childhood dreams has come true."]
      [:p "Our earliest playtesters were Kyle Dawkins, John Brown and Nathan Nifong. Each of them gave critical feedback at a formative time, and each left a signature on the final shape of things."]
      [:p "We took a 5-month-old Sol to our first (and local) convention: Gamestorm. There we met Tim Eisner of Weird City Games and Anthony Gallela of Kubla Con Game Design Contest. Tim and brother Ben have become good friends and great allies in the world of independent game publishing, and Anthony’s wise and ruthless counsel on our rulebook will never be forgotten."]
      [:p "Print and Play Games, founded by the honorable Andrew Tullsen, has printed all of our remarkably high quality prototypes. Thanks to Toby Grubb and Martha Koenig for early guidance in vector graphics. Thank you, Jon Mietling of Portal Dragon, for creating our amazing pieces! And game board! And solar flare. And... eternal gratitude to Mark Dusk, who perfected the Elephant Laboratories logo and gave birth to our stunning Sol logo, which set the tone for the rest of the design. Thank you, Ryan Linstrom for your smart design insights and intestinal fortitude in our post-campaign design marathon. And there aren’t words enough to thank our design finisher and hero, Adam Murdoch, for your detailed eye, tremendous generosity and breathtaking layout of the Mythos book."]
      [:p "Thank you to all the people who printed 3-D pieces for us. I don’t know what drove me to make all these crazy pieces for the game, but it was great to have so many people willing to help out with the printing: Chris Crewdson, David Schaefer, Jon Mietling, Ryan Swisher and Jonathan Liu. Thanks to the early playtesters from PDX Epic Gamers: Mandy, David Abel, Brandon, Seeger and Joel Carson."]
      [:p "From Kublacon 2015, thanks to Paul, Andy and Chris for being our home base there. Thank you to Chad Nicholas and the crew at Geekline415 for your raucous support. Thanks to Kennith Grotjohn for the awesome diagrams you scribbled on the hotel stationery to explain the actions. Thanks to Peter Kral and Lisa and Darien, and everyone else there we shared games and laughs with."]
      [:p "Thank you to everyone in Stumptown Gamecrafters who suffered through our countless changes. Jason Clor, Mohammad Ali, Chase Van Epps, Scott Biersdorf, Peter Shaefer and everyone else who meets every other Monday. Your input and insights have been invaluable."]
      [:p "Thank you to the great people at our local game stores in Portland, OR. James and Kirsten Brady and Ben from Cloud Cap, and Ryan Mauk and Hans from the Portland Game Store: you guys have it figured out."]
      [:p "Thank you for letting Jodi and me be a part of the amazing community you continue to build. Also thank you to Guardian, Red Castle, Rainy Day and Off the Charts for hosting our early playtests, and all the FLGS of the world for your noble service to humanity."]
      [:p "Thank you to Cole Wehrle for incisive perspective and true thoughtfulness. Your review of Sol is a work of art. Also thanks to Josh Boykin, Steve Valladolid and Jonathan Liu for your heartfelt reviews. We are lucky to have you in our corner."]
      [:p "The guys who made our Kickstarter video are amazing! Thank you Jeff Harshman, Brandon Schoessler, Kyle Stebbins, Mark Dusk and Reed Harvey of Digital One for bringing our Mythos and story to life! Thank you to Mischa Krilov for your master guidance through the process of preparing and providing for our Kickstarter community and your love of random abstracts (Chase!)."]
      [:p "Thank you to Moe, Lyndon and Brian of the Board Game Group for your savvy, insight and support. Thank you, Matt Sims of Panda, for working through our budding understanding of what it takes to make a game. And Carol Carmick, thank you for catching every typo and thinking critically about the words of Sol. Your invisible hand girds this very document."]
      [:p "C.J. Hallowell took a kernel of an idea for our Mythos and created something magical. We can never offer enough thanks for sharing the gift of your imagination with us all."]
      [:p "Thank you Linda Bache and Susan Ahrens for your generosity. Thank you to Beth Olsen Photography and Jason Washburn of Talon Strikes for your respective contributions to making our Kickstarter campaign sing. James Hudson of Druid City Games–you, sir, are a force of goodness. Thank you for your tireless support and enthusiasm. Special thanks to the Jersey Boys (Rob Austin and Lance Van Ness) and to Stacy & Brandan Flynn for spreading the good word (and so many Sol playtests) to the Midwest and East Coast. Thank you to Peter Gifford of Universal Head for making our beautiful and succint rules summary! Tak til Thomas Dorf Nielsen for at samle støtte og oversætte på BGG!"]
      [:p "Thank you to all our Kickstarter backers. Especially our Galaxy Backers: Mohammad Ali, John Brown, Andrew Long and Matthew Hawkins; our Sun Backers: Chad Nicholas, Zelbinian (Dustin Hodge), Nathan Meyer, Mike Shiley, Craig Barrie, Portland Gamecraft (Evan Halbert); and our Planet Backers: Will Thomsen, Christopher McHenry, Lucas Kenall, Kyle Dawkins, Brandan \"Xandar13\" Flynn, Tim Eisner, Joseph Reisinger, Dave & Jen King, Bobbie Sweetman, Dan Pancakes Roberts, Jonathan Yost, Lance Van Ness and Mark Waldron; and finally our sole Deity level backer Michael Spangler, whose contribution goes so far beyond this pledge. He is everything a deity should be."]
      [:p "Thank you, Bobbie Sweetman, for your joy and inspiration. You show us what life can be."]
      [:p "Thank you to our mother, Carolyn Spangler and father, Michael Spangler, who gave us so much more than just life. Ma, I wish you could see what your boys have done. I know you would be proud."]
      [:p "But thank you most of all to Jodi Sweetman, without whom Sol would still just be some light beams shining through the branches. You took an idea and gave it a chance to be real. And now it is."]
      [:p "— Ryan"]]]]])


(defn sol-complete
  []
  [:<>
   [:header {:class "container container--pageheader"} 
    [:div {:class "constrainer"} 
     [:div {:class "measure"} 
      [:h1 {:class "h1"} "SIGN-UP SUCCESSFUL!"]]]]
   [:div {:class "container"} 
    [:div {:class "constrainer measure"} 
     [:p "Thank you for subscribing! You will be notified about these projects as information becomes available. "]]]])


(defn organism-nav
  []
  [:nav {:class "headernav"}
   [:ul {:class "headernav__menu"}
    [:li {:class "headernav__menu__item headernav__menu__item--home"}
     [:a {:href "/organism"} "ORGANISM" 
      [:span {:class "headernav__menu__item--home__tagline"} ""]]]
    ;; [:li {:class "headernav__menu__item"}
    ;;  [:a {:href "/sol/story/"} "Gameplay"]]
    ;; [:li {:class "headernav__menu__item"}
    ;;  [:a {:href "/sol/worlds/"} "Mythos"]]
    ;; [:li {:class "headernav__menu__item"}
    ;;  [:a {:href "/sol/background/"} "Media"]]
    [:li {:class "headernav__menu__item"}
     [:a {:href "/sol/buy"} "Buy"]]]])

(defn organism-header
  []
  [:header {:class "container container--homeheader"}
   [:div {:class "constrainer"}
    [:div {:class "masthead measure"}
     [:h1 {:class "h1 masthead__h1"}
      [:img {:src "/img/organism/organism-cover.png", :alt "ORGANISM", :class "masthead__image", :width "1000", :height "270"}]]
     [:h2 {:class "h2 masthead__h2"}
      [:span "From Elephant Laboratories"]]
     [:p "ORGANISM is a game of growth, multiplication, and struggle for 1-6 players. Wielding the simple actions of EAT / GROW / MOVE / CIRCULATE the minimal ruleset is maximally integrated and generates a tight yet boundless decision space. Amplified by a set of mutation cards which expand the rules in countless subtle and not-so-subtle ways, it is really a family of related games in a single box that can never be truly exhausted."]
     [:p {:class "buttonwrap"}
      [:a {:href "#what", :class "button buttonwrap__button"} "Learn More"]
      [:a {:href "/sol/buy", :class "button buttonwrap__button button--outline"} "Sign up!"]]
     [:aside {:class "current-status"}
      [:strong "CURRENT STATUS"]": PLANNING CAMPAIGN!"]]]])

(defn organism-main
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
          [:a {:href "/sol/buy", :class "button button--outline"} "Sign up!"]]]]]
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
         [:a {:href "/sol/story"} "Learn more " 
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
          [:a {:href "/sol/worlds"} "Learn more " 
           [:nobr "about Sol Mythos »"]]]]
        [:div {:class "sol-rule sol-rule--white"}
         [:h2 {:class "h2"} "Videos " 
          [:nobr "and Links"]]
         [:p "Watch how-to’s, play-throughs and interviews with Sol’s design team. Download the complete rulebook. Read all the nice things others are saying " 
          [:nobr "about Sol."]]
         [:p 
          [:a {:href "/sol/background"}
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
         [:a {:href "/sol/thanks"} "We have so many " 
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

(defn organism-footer
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
      [:a {:href "/sol/thanks"} "Game Credits"]]]
    [:p "Built by " 
     [:a {:href "https://www.happyfanfare.com", :rel "external"} "Happy Fanfare"]]]])



(defn home-page
  []
  [:<>
   [home-nav]
   [home-header]
   [home-main]
   [home-footer]])

(defn sol-home-page
  []
  [:<>
   [sol-nav]
   [sol-header]
   [sol-main]
   [sol-footer]])

(defn sol-story-page
  []
  [:<>
   [sol-nav]
   [sol-story]
   [sol-footer]])

(defn sol-worlds-page
  []
  [:<>
   [sol-nav]
   [sol-worlds]
   [sol-footer]])

(defn sol-background-page
  []
  [:<>
   [sol-nav]
   [sol-background]
   [sol-footer]])

(defn sol-buy-page
  []
  [:<>
   [sol-nav]
   [sol-buy]
   [sol-footer]])

(defn sol-thanks-page
  []
  [:<>
   [sol-nav]
   [sol-thanks]
   [sol-footer]])

(defn sol-complete-page
  []
  [:<>
   [sol-nav]
   [sol-complete]
   [sol-footer]])

(defn organism-home-page
  []
  [:<>
   [organism-nav]
   [organism-header]
   [organism-main]
   [organism-footer]])


(def pages
  {:home #'home-page
   :sol-home #'sol-home-page
   :sol-story #'sol-story-page
   :sol-worlds #'sol-worlds-page
   :sol-background #'sol-background-page
   :sol-buy #'sol-buy-page
   :sol-thanks #'sol-thanks-page
   :sol-complete #'sol-complete-page
   :organism-home #'organism-home-page})

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

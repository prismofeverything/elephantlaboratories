(defproject elephantlaboratories "0.0.1"

  :description "site for elephantlaboratories.com"
  :url "https://elephantlaboratories.com"

  :dependencies [[cljs-ajax "0.8.3"]
                 [clojure.java-time "0.3.2"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.cognitect/transit-cljs "0.8.269"]
                 [com.google.guava/guava "33.0.0-jre"]
                 [com.novemberain/monger "3.1.0" :exclusions [com.google.guava/guava]]
                 [cprop "0.1.17"]
                 [expound "0.8.9"]
                 [funcool/struct "1.4.0"]
                 [json-html "0.4.7"]
                 [luminus-immutant "0.2.5"]
                 [luminus-transit "0.1.2"]
                 [markdown-clj "1.10.5"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.13"]
                 [metosin/ring-http-response "0.9.2"]
                 [mount "0.1.16"]
                 [com.novemberain/monger "3.5.0"
                  :exclusions [com.google.guava/guava]]
                 [nrepl "0.8.3"]
                 [org.clojure/clojure "1.11.1"]
                 [thheller/shadow-cljs "2.28.21" :scope "provided"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.webjars.npm/bulma "0.9.2"]
                 [org.webjars.npm/material-icons "0.7.0"]
                 [org.webjars/webjars-locator "0.41"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [reagent "1.1.0"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.9.3"]
                 [ring/ring-defaults "0.3.2"]
                 [selmer "1.12.40"]]

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot elephantlaboratories.core

  ;; ClojureScript is built by shadow-cljs, not Leiningen — see shadow-cljs.edn.
  ;;   npx shadow-cljs watch app       dev, hot reload
  ;;   npx shadow-cljs release app     optimised (release.py build runs this)
  :plugins [[lein-immutant "2.1.0"]]
  ;; Leiningen cleans target/ and nothing else. It must NOT list shadow-cljs's
  ;; output here: :auto-clean defaults to true, so `lein uberjar` cleans first,
  ;; and a jar would then be packaged with the JavaScript freshly deleted.
  ;; Use `npx shadow-cljs clean` (or delete resources/public/js/app.js) instead.
  :clean-targets ^{:protect false} [:target-path]
  

  :profiles
  {:uberjar {:omit-source true
             
             ;; The JS is built out of band by `npx shadow-cljs release app`
             ;; and picked up from resources/public/js — see release.py.
             :prep-tasks ["compile"]
             
             :aot :all
             :uberjar-name "elephantlaboratories.jar"
             :source-paths ["env/prod/clj" ]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn" ]
                  :dependencies [[binaryage/devtools "1.0.3"]
                                 [cider/piggieback "0.5.2"]
                                 [doo "0.1.11"]
                                 [pjstadig/humane-test-output "0.11.0"]
                                 [prone "2021-04-23"]
                                 [ring/ring-devel "1.9.3"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [jonase/eastwood "0.3.5"]
                                 [cider/cider-nrepl "0.26.0"]
                                 ]
                  :doo {:build "test"}
                  :source-paths ["env/dev/clj" ]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn" ]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})

(defproject elephantlaboratories "0.0.1"

  :description "site for elephantlaboratories.com"
  :url "https://elephantlaboratories.com"

  :dependencies [[cljs-ajax "0.8.3"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]
                 [clojure.java-time "0.3.2"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.cognitect/transit-cljs "0.8.269"]
                 [com.google.guava/guava "27.0.1-jre"]
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
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.10.866" :scope "provided"]
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
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot elephantlaboratories.core
  :figwheel {:server-port 3558}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-immutant "2.1.0"]] 
  :clean-targets ^{:protect false}
  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  

  :profiles
  {:uberjar {:omit-source true
             
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-dir "target/cljsbuild/public/js"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :source-map "target/cljsbuild/public/js/app.js.map"
                 :optimizations :advanced
                 :pretty-print false
                 :infer-externs true
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :externs ["react/externs/react.js"]}}}}
             
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
                                 [figwheel-sidecar "0.5.20"]
                                 [pjstadig/humane-test-output "0.11.0"]
                                 [prone "2021-04-23"]
                                 [ring/ring-devel "1.9.3"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [jonase/eastwood "0.3.5"]
                                 [cider/cider-nrepl "0.26.0"]
                                 [lein-doo "0.1.11"]
                                 [lein-figwheel "0.5.20"]] 
                  :cljsbuild
                  {:builds
                   {:app
                    {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                     :figwheel
                     {:on-jsload "elephantlaboratories.core/mount-components"}
                     :compiler
                     {:main "elephantlaboratories.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true}}}}
                  
                  
                  :doo {:build "test"}
                  :source-paths ["env/dev/clj" ]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn" ]
                  :resource-paths ["env/test/resources"] 
                  :cljsbuild 
                  {:builds
                   {:test
                    {:source-paths ["src/cljc" "src/cljs" "test/cljs"]
                     :compiler
                     {:output-to "target/test.js"
                      :main "elephantlaboratories.doo-runner"
                      :optimizations :whitespace
                      :pretty-print true}}}}
                  
                  }
   :profiles/dev {}
   :profiles/test {}})

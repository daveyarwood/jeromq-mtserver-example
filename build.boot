(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.zeromq/jeromq        "0.4.2"]
                  [alandipert/boot-trinkets "2.0.0" :scope "test"]])

(task-options!
  javac {:options ["-source" "1.8"
                   "-target" "1.8"]})

(require '[alandipert.boot-trinkets :as bt])

(deftask run
  "Runs the example's `main` method."
  []
  (comp
    (javac)
    (bt/run :main 'mtserver.Main
            :args [])))


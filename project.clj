(defproject sendandi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.replikativ/datahike "0.3.2-SNAPSHOT"]
                 [com.datomic/client-cloud "0.8.78" :scope "provided"]
                 [com.cognitect/transit-clj "0.8.313"]
                 [org.clojure/tools.cli "1.0.194"]]
  :repl-options {:init-ns sendandi.api})

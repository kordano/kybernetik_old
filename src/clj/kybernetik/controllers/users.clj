(ns kybernetik.controllers.users
  (:require
   [kybernetik.layout :as layout]
   [kybernetik.db.core :as db]
   [clojure.java.io :as io]
   [kybernetik.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn index [request]
  (let [attrs [:user/name
               :user/firstname
               :user/lastname
               :user/role]
        rows (mapv (fn [user]
                     (mapv (fn [a] (if (= :user/role a)
                                     (-> user :user/role :db/ident)
                                     (a user))) attrs))
                   (db/list-users))]
    (layout/render request "index.html" {:model "user"
                                         :attrs attrs
                                         :rows  rows})))



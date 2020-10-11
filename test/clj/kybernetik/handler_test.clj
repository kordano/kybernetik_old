(ns kybernetik.handler-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer :all]
   [kybernetik.handler :refer :all]
   [kybernetik.middleware.formats :as formats]
   [muuntaja.core :as m]
   [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

#_(use-fixtures
    :once
    (fn [f]
      (mount/start #'kybernetik.config/env
                   #'kybernetik.handler/app-routes)
      (f)))

#_(deftest test-app
    (testing "main route"
      (let [response ((app) (request :get "/"))]
        (is (= 200 (:status response)))))

    (testing "not-found route"
      (let [response ((app) (request :get "/invalid"))]
        (is (= 404 (:status response))))))

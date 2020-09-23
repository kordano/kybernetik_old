(ns kybernetik.layout
  (:require
    [clojure.java.io]
    [selmer.parser :as parser]
    [selmer.filters :as filters]
    [hiccup.core :as h]
    [hiccup.page :as hp]
    [clojure.string :as s]
    [markdown.core :refer [md-to-html-string]]
    [ring.util.http-response :refer [content-type ok]]
    [ring.util.anti-forgery :refer [anti-forgery-field]]
    [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
    [ring.util.response :refer [redirect]]))

(parser/set-resource-path!  (clojure.java.io/resource "html"))
(parser/add-tag! :csrf-field (fn [_ _] (anti-forgery-field)))
(filters/add-filter! :markdown (fn [content] [:safe (md-to-html-string content)]))

(defn header [{:keys [title]}]
  [:head
   [:title (or title "Kybernetik")]
   (hp/include-css "/assets/bulma/css/bulma.min.css"
                   "/assets/material-icons/css/material-icons.min.css"
                   #_"/css/screen.css")])

(def footer
  [:script {:type "text/javascript"}
   "(function() {
        var burger = document.querySelector('.burger');
        var nav = document.querySelector('#'+burger.dataset.target);
        burger.addEventListener('click', function(){
          burger.classList.toggle('is-active');
          nav.classList.toggle('is-active');
        });
      })();"])

(defn navbar [{:keys [page]}]
  [:nav.navbar.is-light
   [:div.container
    [:div.navbar-brand
     [:a.navbar-item {:href "/"} "kybernetik"]]
    [:div.navbar-menu
     [:div.navbar-start
      [:a {:href "/users"
           :class (str "navbar-item" (if (= page "users") " is-active" ""))}
       "Users"]]]]])

(defn base [content & [{{:keys [type text] :as message} :message :as params}]]
  (hp/html5
   (header params)
   (navbar params)
   [:section.section
    [:div.container
     [:article {:class (str "message "
                            (if message "" "is-hidden ")
                            (if message
                              (case type
                                :info "is-info"
                                :success "is-success"
                                :warn "is-warning"
                                :error "is-danger"
                                "is-primary")
                              ""))}
      [:div.message-header
       [:p (case type
             :info "Info"
             :success "Success"
             :warn "Warning"
             :error "danger"
             "Message")]
       [:button.delete {:aria-label :delete}]]
      [:div.message-body
       text]]
     content]]
   footer))


(defn index [{:keys [attrs rows model]}]
  [:div.content
   [:h2 (str "Listing " (s/capitalize model) "s")]
   [:table
    [:thead [:tr
             (for [a attrs]
               [:td a])
            [:td ""] ]]
    [:tbody
     (for [row rows]
       [:tr
        (for [r row]
          [:td r])
        (let [id (str (first row))]
          [:td
           [:div.buttons
            [:a.button.is-info {:href (str model "s/" id "/show")  } "show"]
            [:a.button.is-warning {:href (str model "s/" id "/edit")} "edit"]
            (hiccup.form/form-to
             [:post (str "/" model "s/" id "/patch")]
             (anti-forgery-field)
             [:input.input.is-hidden {:id "_method"
                                      :name "_method"
                                      :value "delete"}]
             [:input.button.is-danger {:type :submit :value "delete"}])
            ]])])]]
   [:a.button.is-link {:href (str model "s/new")} (str "New " (s/capitalize model))]])

(defn new [{:keys [attrs model]}]
  [:div.content
   [:a.button.is-link {:href (str "/" model "s")} "Back"]
   [:h2.title (str "New " (s/capitalize model))]
   [:div.content
    (hiccup.form/form-to
     [:post (str "/" model "s")]
     (anti-forgery-field)
     (for [[k {:keys [type placeholder]}] attrs]
       (let [attr (name k)]
         [:div.field
          [:label.label {:for attr} k]
          (case type
            :selection [:div.select [:select {:id attr
                                              :name attr}
                                     (for [o placeholder]
                                       [:option {:value (name o)} (name o)])]]
            [:input.input {:id attr
                           :name attr
                           :type type
                           :placeholder placeholder}])]))
     [:input.button.is-success {:type :submit :value "Save"}])]])

(defn show [{:keys [model entity]}]
  [:div.content
   [:a.button.is-link {:href (str "/" model "s")} "Back"]
   [:h2.title (str "Show " (s/capitalize model))]
   [:div.content
    [:ul
     (for [[k v] entity]
       [:li
        [:strong (name k)] " "
        v])]]])

(defn edit [{:keys [model attrs values id]}]
  [:div.content
   [:a.button.is-link {:href (str "/" model "s")} "Back"]
   [:h2.title (str "New " (s/capitalize model))]
   [:div.content
    (hiccup.form/form-to
     [:post (str "/" model "s/" id "/patch")]
     (anti-forgery-field)
     (for [[k {:keys [type placeholder]}] attrs]
       (let [attr (name k)]
         [:div.field
          [:label.label {:for attr} k]
          (case type
            :selection [:div.select [:select {:id attr
                                              :name attr}
                                     (for [o placeholder]
                                       [:option
                                        {:value (name o)
                                         :selected (= o (k values))}
                                        (name o)])]]
            [:input.input {:id attr
                           :name attr
                           :type type
                           :value (k values)
                           :placeholder placeholder}])]))
     [:input.button.is-success {:type :submit :value "Save"}])]])


(defn render
  "renders the HTML template located relative to resources/html"
  [request content & [params]]
  (content-type
   (ok
    (base content params))
   "text/html; charset=utf-8"))

(defn render-template
  "renders the HTML template located relative to resources/html"
  [request template & [params]]
  (content-type
    (ok
     (parser/render-file
      template
      (assoc params
             :page template
             :csrf-token *anti-forgery-token*)))
    "text/html; charset=utf-8"))

(defn error-page
  "error-details should be a map containing the following keys:
   :status - error status
   :title - error title (optional)
   :message - detailed error message (optional)

   returns a response map with the error page as the body
   and the status specified by the status key"
  [error-details]
  {:status  (:status error-details)
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (parser/render-file "error.html" error-details)})

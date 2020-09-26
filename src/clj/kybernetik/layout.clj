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
                   "/css/mdi/css/materialdesignicons.min.css"
                   "/css/screen.css")])

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
      [:a {:href "/projects"
           :class (str "navbar-item" (if (= page "projects") " is-active" ""))}
       "Projects"]
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

(defn go-back [model]
  [:a.is-link {:href (str "/" model "s")} "Back"])

(defn action-buttons [model id]
  [:div.buttons
   [:a {:href (str "/" model "s/" id "/edit")}
    [:span.icon.is-medium
     [:i.mdi.mdi-square-edit-outline.mdi-24px.mdi-dark]]]
   [:a {:href (str "/" model "s/" id "/delete")}
    [:span.icon.has-text-danger.is-medium
     [:i.mdi.mdi-delete.mdi-24px]]]])

(defn card-footer-edit [model id]
  [:a.card-footer-item {:href (str "/" model "s/" id "/edit")} "Edit"])

(defn card-footer-delete [model id]
  [:a.card-footer-item.has-text-danger {:href (str "/" model "s/" id "/delete")} "Delete"])

(defn card-footer-show [model id]
  [:a.card-footer-item {:href (str "/" model "s/" id "/show")} "Show"])

(defn details
  "Details card for model with header, table contents, and footer with actions"
  [model view tbody actions & {:keys [title-postfix thead listing? back-btn?]
                               :or {title-postfix ""
                                    listing? false
                                    back-btn? true}}]
  [:div.content
   [:div.card
    [:header.card-header
     [:p.card-header-title (s/join " " [view (str (s/capitalize model) (if listing? "s" "")) title-postfix])]]
    [:div.card-content
     [:div.content
      [:table.table
       (when thead
         [:thead thead])
       [:tbody
        tbody]]]
     [:footer.card-footer
      (when back-btn?
        [:div.card-footer-item
         [:a {:href (str "/" model "s")} (str "Back to " (s/capitalize model) "s")]])
      (for [action actions]
        action)]]]])

(defn index [{:keys [attrs rows model]}]
  (let [thead [:tr
               (for [a attrs]
                 [:th a])
               [:td ""]]
        tbody (for [row rows]
                (let [id (str (first row))]
                  [:tr
                   [:th
                    [:a.is-link
                     {:href (str model "s/" id "/show")}
                     id]]
                   (for [r (rest row)]
                     (if (vector? r)
                       (if (vector? (first r))
                         [:td
                          (for [[ref text] r]
                            [:a.index-refs {:href ref} text])]
                         (let [[ref text] r]
                           [:td [:a {:href ref} text]]))
                       [:td r]))
                   [:td
                    [:div.buttons
                     [:a {:href (str model "s/" id "/edit")}
                      [:span.icon.is-medium
                       [:i.mdi.mdi-square-edit-outline.mdi-24px.mdi-dark]]]
                     [:a {:href (str model "s/" id "/delete")}
                      [:span.icon.has-text-danger.is-medium
                       [:i.mdi.mdi-delete.mdi-24px]]]]]]))
        actions [[:a.card-footer-item {:href (str model "s/new")} (str "New " (s/capitalize model))]]]
    (details model "Listing" tbody actions :thead thead :listing? true :back-btn? false)))

(defn new [{:keys [attrs model]}]
  (let [tbody (for [[k {:keys [type placeholder]}] attrs]
                (let [attr (name k)]
                  [:tr
                   [:th k]
                   [:td
                    (case type
                      :multi-selection [:div.select.is-mulitple
                                        {:style "margin-bottom: 10rem !important;"}
                                        [:select {:id attr
                                                  :size "5"
                                                  :multiple true
                                                  :name attr}
                                         (for [[oid oname] placeholder]
                                           [:option {:value (if (keyword oid)
                                                              (name oid)
                                                              oid)
                                                     :selected (= oid :employee)}
                                            (name oname)])]]
                      :selection [:div.select [:select {:id attr
                                                        :name attr}
                                               (for [[oid oname] placeholder]
                                                 [:option {:value (if (keyword oid)
                                                                    (name oid)
                                                                    oid)}
                                                  (name oname)])]]
                      [:input.input {:id attr
                                     :name attr
                                     :type type
                                     :placeholder placeholder}])]]))
        actions [[:input.card-footer-item.card-action-item {:type :submit :value "Save"}]]]
    [:form {:action (str "/" model "s")
            :method "POST"}
     (anti-forgery-field)
     (details model "New" tbody actions)]))

(defn show [{:keys [model entity id]}]
  (let [tbody (for [[k v] entity]
                [:tr
                 [:th (name k)]
                 [:td
                  (if (vector? v)
                    (if (vector? (first v))
                      (for [[ref text] v]
                        [:a {:href ref} text])
                      (let [[ref text] v]
                        [:a {:href ref} text]))
                    v)]])
        actions [(card-footer-edit model id)
                 (card-footer-delete model id)]]
    (details model "Show" tbody actions :title-postfix id)))

(defn delete [{:keys [model entity id]}]
  (let [tbody (for [[k v] entity]
                [:tr
                 [:th (name k)]
                 [:td
                  (if (vector? v)
                    (if (vector? (first v))
                      (for [[ref text] v]
                        [:a {:href ref} text])
                      (let [[ref text] v]
                        [:a {:href ref} text]))
                    v)]])
        actions [(card-footer-edit model id)
                 [:div.card-footer-item
                  [:form {:action (str "/" model "s/" id "/patch")
                          :method "POST"}
                   (anti-forgery-field)
                   [:input.input.is-hidden {:id "_method"
                                            :name "_method"
                                            :value "delete"}]
                   [:input.card-action-item.has-text-danger {:type :submit
                                             :value "Delete"}]]]]]
    (details model "Delete" tbody actions :title-postfix id)))

(defn edit [{:keys [model attrs values id]}]
  (let [tbody (for [[k {:keys [type placeholder]}] attrs]
                (let [attr (name k)]
                  [:tr
                   [:th k]
                   [:td
                    (case type
                      :multi-selection [:div.select.is-mulitple
                                        {:style "margin-bottom: 10rem !important;"}
                                        [:select {:id attr
                                                  :size "5"
                                                  :multiple true
                                                  :name attr}
                                         (for [[oid oname] placeholder]
                                           [:option {:value (if (keyword oid)
                                                              (name oid)
                                                              oid)
                                                     :selected (= oid (k values))}
                                            (name oname)])]]
                      :selection [:div.select [:select {:id attr
                                                        :name attr}
                                               (for [[oid oname] placeholder]
                                                 [:option {:value (if (keyword oid)
                                                                    (name oid)
                                                                    oid)
                                                           :selected (= oid (first (k values)))} (name oname)])]]
                      [:input.input {:id attr
                                     :name attr
                                     :type type
                                     :value (k values)
                                     :placeholder placeholder}])]]))
        actions [(card-footer-show model id)
                 [:input.card-footer-item.card-action-item {:type :submit :value "Save"}]]]
    [:form {:action (str "/" model "s/" id "/patch")
            :method "POST"}
     (anti-forgery-field)
     (details model "Edit" tbody actions)]))

(defn welcome []
  [:div.content
   [:h2 "Welcome user"]])

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

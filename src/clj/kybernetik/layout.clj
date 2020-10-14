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

(defn navlink [page model]
  [:a {:href (str "/" model "s")
       :class (str "navbar-item" (if (= page (str model "s")) " is-active" ""))}
   (str (s/capitalize model) "s")])

(defn navbar [{:keys [page signed-in? identity]}]
  [:nav.navbar.is-light
   [:div.container
    [:div.navbar-brand
     [:a.navbar-item {:href "/"} "kybernetik"]]
    [:div.navbar-menu
     (when signed-in?
       [:div.navbar-start
        (navlink page "timesheet")
        (navlink page "project")
        (navlink page "user")])
     [:div.navbar-end
      (when signed-in?
        [:p.navbar-item.has-text-grey.is-size-7 "Signed in as " identity])
      [:div.buttons
       (if signed-in?
         [:a.button {:href "/sign-out"} "Sign out"]
         [:a.button.is-primary {:href "/sign-in"} "Sign in"])]]]]])

(defn base [{{:keys [identity]} :session} content & [{{:keys [type text] :as message} :message :as params}]]
  (hp/html5
   (header params)
   (navbar (if identity
             (assoc params :signed-in? true :identity identity)
             params))
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
  [model view tbody actions & {:keys [title-postfix thead listing? back-btn? actions?]
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
        tbody]]
      (when (empty? tbody)
        [:p.has-text-grey.center (str "No results found.")])]
     [:footer.card-footer
      (when back-btn?
        [:div.card-footer-item
         [:a {:href (str "/" model "s")} (str "Back to " (s/capitalize model) "s")]])
      (for [action actions]
        action)]]]])

(defn index [{:keys [attrs rows model editable? actions]
              :or {editable? true
                   actions {:new {}}}}]
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
                     (cond
                       (vector? r) (if (vector? (first r))
                                     [:td
                                      (for [[ref text] r]
                                        [:a.index-refs {:href ref} text])]
                                     (let [[ref text] r]
                                       [:td [:a {:href ref} text]]))
                       (boolean? r) [:td
                                     (if r
                                       [:span.icon.has-text-success
                                        [:i.mdi.mdi-check-circle]]
                                       [:span.icon.has-text-danger
                                        [:i.mdi.mdi-close-circle]])]
                       :else [:td r]))
                   [:td
                    (when editable?
                      [:div.buttons
                       [:a {:href (str model "s/" id "/edit")}
                        [:span.icon.is-medium
                         [:i.mdi.mdi-square-edit-outline.mdi-24px.mdi-dark]]]
                       [:a {:href (str model "s/" id "/delete")}
                        [:span.icon.has-text-danger.is-medium
                         [:i.mdi.mdi-delete.mdi-24px]]]])]]))
        action-items (for [[a params] actions]
                       (let [param-str (if-not (empty? params)
                                         (str "?" (reduce (partial clojure.string/join "&") (map (fn [[k v]] (str (name k) "=" v)) params)))
                                         "")]
                         [:a.card-footer-item {:href (str "/" model "s/" (name a) param-str)} (str (s/capitalize (name a)) " " (s/capitalize model))]))]
    (details model "Listing" tbody action-items :thead thead :listing? true :back-btn? false)))

(defn new [{:keys [attrs model submit-params title-postfix]}]
  (let [tbody (for [[k {:keys [type placeholder min max]}] attrs]
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
                                     :min min
                                     :max max
                                     :placeholder placeholder}])]]))
        actions [[:input.card-footer-item.card-action-item {:type :submit :value "Save"}]]
        query-str (if-not (empty? submit-params)
                    (str "?" (reduce (partial clojure.string/join "&") (map (fn [[k v]] (str (name k) "=" v)) submit-params)))
                    "")]
    [:form {:action (str "/" model "s" query-str)
            :method "POST"}
     (anti-forgery-field)
     (details model "New" tbody actions :title-postfix title-postfix)]))

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

(defn welcome [{:keys [session] :as request}]
  [:section.hero
   [:div.hero-body
    [:div.container
     [:h1.title (if-let [id (:identity session)]
                  (str "Welcome " id)
                  (str "Not logged in."))]]]])

(defn sign-in []
  [:div.columns.is-centered
   [:div.column.card.is-half
    [:header.card-header
     [:p.card-header-title "Sign in"]]
    [:form {:action "/sign-in" :method "POST"}
     (anti-forgery-field)
     [:div.card-content
      [:div.content
       [:div.field
        [:label.label {:for "email"} "Email"]
        [:input.input {:id "email"
                       :name "email"
                       :type "email"
                       :placeholder "max@mustermann.de"}]]
       [:div.field
        [:label.label {:for "password"} "Password"]
        [:input.input {:id "password"
                       :type "password"
                       :name "password"
                       :placeholder "12345"}]]]
      [:footer.card-footer
       [:input.card-footer-item.card-action-item {:type :submit :value "Sign in"}]]]]]])

(defn container [& more]
  [:div.container
   more])

(defn render
  "renders the HTML template located relative to resources/html"
  [request content & [params]]
  (content-type
   (ok
    (base request content params))
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

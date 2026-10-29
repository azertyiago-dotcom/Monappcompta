# 📱 RECAP PROJET — Ma Compta (Android WebView)

> Fichier de reprise. À fournir à l'assistant IA au début de chaque nouvelle session.
> Dernière mise à jour : 2026-10-30

---

## 🎯 Objectif du projet

Application Android native (WebView) pour la gestion de comptabilité d'un petit commerce :
- Gestion de stock (produits, prix TTC, quantités)
- Enregistrement d'articles au panier (depuis stock ou saisie libre)
- Encaissement avec choix du mode de paiement (Espèces / Chèque / Carte SumUp)
- Historique des factures clôturées
- Bilan du jour (CA TTC)
- Informations établissement (nom + SIRET)

**Public cible :** commerçants indépendants, usage personnel / terrain.

---

## 🛠️ Stack technique

| Composant | Choix |
|-----------|-------|
| Langage | Java (Android natif) |
| UI | WebView + HTML/CSS/JS vanilla |
| Stockage | `localStorage` (⚠️ à migrer vers Room/SQLite) |
| Build | Gradle 8.7 + AGP 8.5.2 |
| CI/CD | GitHub Actions → APK debug |
| SDK | compileSdk 34, targetSdk 34, minSdk 23 |
| Java | 17 |
| Namespace | `com.monentreprise.macompta` |

---

## 📁 Arborescence finale du dépôt
├── .github/
│ └── workflows/
│ └── build.yml
├── index.html
├── AndroidManifest.xml
├── build.gradle
├── settings.gradle
├── gradle.properties
├── proguard-rules.pro
└── src/
└── main/
├── java/com/monentreprise/macompta/
│ └── MainActivity.java
└── res/
├── values/
│ ├── strings.xml
│ └── themes.xml
└── xml/
├── data_extraction_rules.xml
└── backup_rules


---

## ✅ Bugs corrigés (historique)

### Manifest
- ❌ `xmlns:android="http://android.com"` → ✅ `http://schemas.android.com/apk/res/android`
- ❌ `allowBackup="true"` (fuite SIRET/CA) → ✅ `false` + règles d'exclusion
- ❌ `<queries>` SumUp inutile (sans SDK) → ✅ supprimé
- ❌ Thème `@android:style/...` → ✅ thème AppCompat custom
- ✅ Ajout `configChanges` + `windowSoftInputMode="adjustResize"`

### Workflow GitHub Actions
- ❌ `echo "" > settings.gradle` (vidait le fichier) → ✅ supprimé
- ❌ `jakejarvis/lighthouse-action` (hors sujet) → ✅ supprimé
- ❌ `native-actions/copy-file@v1` + `echo` multi-lignes fragiles → ✅ remplacés par fichiers versionnés
- ❌ Pas de `build.gradle` généré → ✅ tout versionné dans le dépôt
- ✅ Setup Android SDK + Gradle 8.7 officiels

### Gradle
- ✅ Ajout `dependencies` (appcompat + webkit) — manquait
- ✅ Ajout `sourceSets` pour l'arborescence custom
- ✅ Ajout `compileOptions` Java 17
- ✅ `noCompress` sur html/js/css (WebView)
- ✅ `minSdk` 21 → 23 (obsolescence)

### MainActivity.java
- ✅ `WebView.setWebContentsDebuggingEnabled` **uniquement en debug**
- ✅ `allowFileAccessFromFileURLs` et `allowUniversalAccessFromFileURLs` à `false`
- ✅ Gestion du bouton retour (`onBackPressed`)
- ✅ `onDestroy` libère la WebView (fuites mémoire)
- ✅ `Bridge` JS prêt pour futures intégrations natives
- ✅ Extension `AppCompatActivity`

### index.html
- ❌ `grid-template-cols` → ✅ `grid-template-columns`
- ❌ `render()` manquant/tronqué (rien ne s'affichait) → ✅ complet
- ❌ Partie après `ventes.push(nouvelleVente);` tronquée → ✅ réécrite
- ✅ `loadAll()` / `saveStocks()` / `saveVentes()` factorisés + try/catch
- ✅ `clearAll()` protégé par double confirmation
- ✅ Échappement HTML `esc()` (anti-injection)
- ✅ Détail des articles dans chaque facture (`items`)
- ✅ Suppression d'articles du panier (`removePanier`)
- ✅ Format monétaire centralisé `fmt()`
- ✅ États vides explicites
- ✅ Viewport mobile + `user-scalable=no`

---

## ⚠️ Points en suspens / à traiter

### 🔴 Priorité haute
1. **`localStorage` peut être vidé par Android** (nettoyage cache, "effacer données").
   → Perte totale du stock, ventes, profil.
   → **Migration nécessaire vers Room/SQLite** + export/import JSON/CSV.
   → Impact : gravité maximale pour une app de compta.

2. **SumUp n'est pas vraiment intégré.**
   - Le `<select>` étiquette juste le moyen de paiement.
   - Pour encaisser réellement : intégrer SDK SumUp (`com.sumup:merchant-sdk`).
   - Vrai projet à part (auth OAuth, terminal, callback paiement).

### 🟡 Priorité moyenne
3. **Sécurité des données**
   - SIRET + CA en clair dans localStorage.
   - Envisager SQLCipher ou EncryptedSharedPreferences.
   - Mention RGPD si publication Play Store.

4. **Export comptable**
   - Aucun export CSV/PDF des factures.
   - Un comptable en aura besoin.

5. **Numérotation des factures**
   - Actuellement : `"F-" + Date.now().toString().slice(-6)` → non séquentielle.
   - Une vraie compta exige une numérotation **continue et chronologique** (obligation légale en France).

### 🟢 Améliorations futures
6. Thème sombre
7. Mode multi-utilisateurs / multi-caisses
8. Sauvegarde cloud (Google Drive / Dropbox)
9. Widget CA du jour sur l'écran d'accueil
10. Impression Bluetooth de tickets

---

## 🚀 État actuel

- ✅ Tous les fichiers sont réécrits et optimisés
- ✅ Build devrait passer sur GitHub Actions
- ⏳ **À tester** : pousser sur `main` → vérifier le build → installer l'APK → test manuel complet

**Checklist test manuel :**
- [ ] App s'ouvre sans crash
- [ ] Champ nom + SIRET persiste après fermeture/réouverture
- [ ] Création d'article en stock fonctionne
- [ ] Ajout au panier depuis stock fonctionne
- [ ] Ajout au panier en saisie libre fonctionne
- [ ] Suppression d'un article du panier fonctionne
- [ ] Encaissement décrémente le stock
- [ ] CA du jour se met à jour
- [ ] Historique affiche la facture clôturée
- [ ] Bouton retour Android ne ferme pas brutalement
- [ ] Rotation d'écran ne perd pas le panier
- [ ] "Tout Réinitialiser" demande bien 2 confirmations

---

## 📞 Reprise de session — phrase à copier

> "Je reprends le projet **Ma Compta** (Android WebView). Voici mon fichier de reprise : [coller ce contenu]. Peux-tu relire l'état actuel et continuer à partir des points en suspens ?"

---

## 📌 Rappels importants pour l'assistant

- L'utilisateur est **francophone** → toujours répondre en français.
- L'utilisateur **n'est pas développeur expert** → expliquer simplement, fournir les fichiers complets (pas juste des extraits).
- L'utilisateur **privilégie la fiabilité** à la complexité : pas de dépendances exotiques.
- Toujours fournir le **fichier complet** quand on corrige, pas un diff partiel.
- Toujours mentionner les **risques légaux** (compta, RGPD, SumUp, Play Store).

---

*Fin du récapitulatif.*

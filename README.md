<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCropsLoots

*Les récoltes de cultures ont une chance de donner des loots bonus (items custom, clés, exp).*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Les récoltes de cultures ont une chance de donner des loots bonus (items custom, clés, exp).

Utilisé sur : **Opale**.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shcropsloots add <nom>` | — | Ajoute l'objet en main à `loots.yml`. | `shcropsloots.admin.add` |
| `/shcropsloots remove <nom>` | — | Retire un loot. | `shcropsloots.admin.remove` |
| `/shcropsloots list` | — | Liste les loots enregistrés. | `shcropsloots.admin.list` |
| `/shcropsloots give <nom> [joueur]` | — | Donne un exemplaire d'un loot. | `shcropsloots.admin.give` |
| `/shcropsloots reload` | — | Relit `config.yml`, `loots.yml` et `messages.yml`. Un fichier illisible ne change rien. | `shcropsloots.admin.reload` |

Sans argument, ou avec une sous-commande inconnue, une ligne d'usage ne liste que les sous-commandes permises à l'expéditeur. `add` et `give` sans joueur ciblé demandent un joueur ; les autres marchent depuis la console.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcropsloots.admin` | Toute l'administration (parent des cinq nœuds suivants). | `op` |
| `shcropsloots.admin.add` | Ajouter l'objet en main à la table des loots. | `op` |
| `shcropsloots.admin.remove` | Retirer un loot. | `op` |
| `shcropsloots.admin.list` | Lister les loots. | `op` |
| `shcropsloots.admin.give` | Donner un exemplaire d'un loot. | `op` |
| `shcropsloots.admin.reload` | Recharger la configuration. | `op` |

## Messages

Tous les textes sont dans `messages.yml`, dont celui d'un loot trouvé (`loot-found`, qui était la clé `globalMessage` de `config.yml`). Au premier démarrage avec cette version, `messages.yml` est créé à partir de la valeur de `globalMessage` du `config.yml` existant ; cette clé n'est plus lue ensuite.

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHCropsLoots/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/messages.yml">messages.yml</a>
└── loots.yml            créé par /shcropsloots add (objets sérialisés)
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>

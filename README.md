# Chronoseal

Capsules temporelles chiffrées, déployées en microservices sur Kubernetes.

On dépose un message et une date d'ouverture. Jusqu'à cette date, personne ne
peut lire le contenu : ni l'utilisateur, ni l'API, ni l'administrateur de la base
de données. À l'échéance, la capsule s'ouvre et chacun peut vérifier que le texte
révélé est bien celui qui avait été déposé.

Projet réalisé par **Ali Kafagy**, en binôme avec **Ahmed Kafagy**, pour le
module Cloud (projet 2026) et les TP du module DevOps.

## Le problème

Une date d'ouverture vérifiée uniquement par l'API ne protège rien : quiconque
lit la base lit les messages. Chronoseal sépare donc deux responsabilités :

- stocker et exposer les capsules ;
- détenir la clé et refuser de déchiffrer avant l'heure.

## Cas d'usage

- lettre à soi-même dans cinq ans ;
- pronostics entre amis, vérifiables après coup ;
- sujet d'examen ou résultats de concours sous embargo ;
- offres remises sous pli scellé.

## Feuille de route

Le dépôt suit la progression du sujet de projet et les TP DevOps. Chaque commit
indique l'étape à laquelle il répond (`[Cloud 10/20]`, `[TP01 §2]`, etc.).

| Étape | Contenu | État |
|---|---|---|
| Cloud 10/20 | Un service, Docker, Deployment et Service Kubernetes | à faire |
| Cloud 12/20 | Gateway | à faire |
| Cloud 14/20 | Deuxième service relié au premier (gRPC) | à faire |
| Cloud 16/20 | Base de données, puis volume | à faire |
| Cloud 18/20 | Sécurité du cluster (RBAC, mTLS, registre) | à faire |
| Cloud 20/20 | Déploiement dans un cloud | à faire |
| TP01 | CI observable et bloquante | à faire |
| TP02 | Chaîne de production des images | à faire |

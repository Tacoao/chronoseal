# Déploiement local sur Minikube

Il faut Docker, Minikube, kubectl (Kustomize est inclus), `istioctl` et
`openssl`.

```bash
minikube start --cpus=2 --memory=5000 --driver=docker
```

## Installer Istio

Le profil `demo` installe le plan de contrôle et l'ingress gateway.

```bash
istioctl install --set profile=demo -y
kubectl -n istio-system get pods
```

Le namespace `chronoseal` a le label `istio-injection: enabled`, donc chaque pod
reçoit un proxy Envoy. C'est pour ça qu'on voit `2/2` dans la colonne `READY`.

## Construire les images dans Minikube

L'overlay `minikube` utilise des images locales taguées `dev`, construites
directement dans le Docker de Minikube. Le contrat gRPC est passé avec
`--build-context`.

```bash
eval $(minikube docker-env)
docker build --build-context proto=proto -t chronoseal/capsule-service:dev apps/capsule-service
docker build --build-context proto=proto -t chronoseal/seal-service:dev apps/seal-service
eval $(minikube docker-env --unset)
```

## Créer la clé maîtresse

Sans clé, seal-service ne démarre pas. On la met dans un Secret créé à la main,
jamais dans le dépôt.

```bash
kubectl create namespace chronoseal
kubectl -n chronoseal create secret generic seal-master-key \
  --from-literal=key-id=k1 \
  --from-literal=master-key="$(openssl rand -base64 32)"
```

Si ce Secret est perdu, toutes les capsules deviennent illisibles. En dehors
d'un test, il faut le sauvegarder.

## Déployer

```bash
kubectl apply -k k8s/overlays/minikube
kubectl -n chronoseal get deployments,pods,services
```

| Ressource | Répliques | Exposition |
|---|---:|---|
| `capsule-service` | 1 (base encore en mémoire) | gateway, `/api/capsules` |
| `seal-service` | 2 (sans état) | interne, gRPC 50051 |

La base `k8s/base` pointe vers les images publiées par la CI sur GHCR. Elle
s'applique avec `kubectl apply -k k8s/base`.

## Passer par la gateway

La Gateway Istio écoute sur le port 80 de l'ingress gateway, et le
VirtualService ne route que `/api/capsules`. Actuator et seal-service ne sont
pas joignables depuis l'extérieur.

```bash
kubectl -n istio-system port-forward deployment/istio-ingressgateway 31380:8080
```

Dans un autre terminal :

```bash
curl -s -X POST http://localhost:31380/api/capsules \
  -H 'Content-Type: application/json' \
  -d '{"title": "Test", "message": "Bonjour le futur", "unlockAt": "2030-01-01T00:00:00Z"}'
curl -s http://localhost:31380/api/capsules
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:31380/actuator/health   # 404
```

## Voir la répartition des appels gRPC

gRPC garde une connexion HTTP/2 ouverte. Sans service mesh, le Service
Kubernetes équilibre au niveau TCP et tous les appels d'un client partiraient
vers la même réplique. Envoy répartit chaque requête, et on voit des scellements
dans les logs des deux répliques.

```bash
kubectl -n chronoseal logs -l app=seal-service -c seal-service --prefix | grep scellée
```

## Tester un service sans la gateway

```bash
kubectl -n chronoseal port-forward service/capsule-service 8080:8080
curl -s http://localhost:8080/actuator/health
```

## Publier sur Docker Hub

La CI publie sur GHCR. Pour Docker Hub, on le fait à la main, en remplaçant
`<compte>` par le compte Docker Hub :

```bash
docker login
docker build --build-context proto=proto -t <compte>/chronoseal-capsule-service:0.1.0 apps/capsule-service
docker push <compte>/chronoseal-capsule-service:0.1.0
```

## Nettoyer

```bash
kubectl delete -k k8s/overlays/minikube
minikube stop
```

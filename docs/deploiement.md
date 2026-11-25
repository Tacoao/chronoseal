# Déploiement local sur Minikube

## Prérequis

- Docker en fonctionnement ;
- Minikube et kubectl (Kustomize est intégré à `kubectl`) ;
- `istioctl` pour installer le service mesh.

```bash
minikube start --cpus=2 --memory=5000 --driver=docker
```

## Installer Istio

Le profil `demo` installe le plan de contrôle et l'ingress gateway.

```bash
istioctl install --set profile=demo -y
kubectl -n istio-system get pods
```

Le namespace `chronoseal` porte le label `istio-injection: enabled` : chaque pod
reçoit automatiquement un proxy Envoy (d'où `2/2` dans la colonne `READY`).

## Construire les images dans Minikube

L'overlay `minikube` utilise des images locales taguées `dev`, construites
directement dans le démon Docker de Minikube.

```bash
minikube image build -t chronoseal/capsule-service:dev apps/capsule-service
```

## Déployer

```bash
kubectl apply -k k8s/overlays/minikube
kubectl -n chronoseal get deployments,pods,services
```

Le manifeste de base (`k8s/base`) référence les images publiées par la CI dans
GitHub Container Registry. Il s'applique avec `kubectl apply -k k8s/base`.

## Accéder à l'API par la gateway

La `Gateway` Istio écoute sur le port 80 de l'ingress gateway. Le
`VirtualService` ne route que le préfixe `/api/capsules` : Actuator et les
services internes restent inaccessibles depuis l'extérieur.

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

## Tester un service sans la gateway

```bash
kubectl -n chronoseal port-forward service/capsule-service 8080:8080
curl -s http://localhost:8080/actuator/health
```

## Publier une image sur Docker Hub

La CI publie les images dans GHCR. Pour une publication manuelle sur Docker Hub
(remplacer `<compte>` par le compte Docker Hub) :

```bash
docker login
docker build -t <compte>/chronoseal-capsule-service:0.1.0 apps/capsule-service
docker push <compte>/chronoseal-capsule-service:0.1.0
```

## Nettoyer

```bash
kubectl delete -k k8s/overlays/minikube
minikube stop
```

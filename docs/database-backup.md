# PostgreSQL 정기 백업

- CronJob: `day-to-us/postgres-backup`
- 매일 03:00, `Asia/Seoul` (한국 시간)
- PostgreSQL 16 `pg_dump -Fc`로 daytous DB 전체 스키마·데이터를 압축 백업
- 저장소: `postgres-backup-pvc` (1Gi 요청, local-path), `/backup/daytous-<UTC시각>-<Pod이름>.dump`
- 최신 백업 성공 후 30일 이상 지난 완료 파일만 삭제. 실패한 실행은 기존 백업을 지우지 않음.
- 부분 파일은 완료 파일과 구분하고 종료 시 제거. 백업 동시 실행 금지, 실패 시 최대 2회 재시도, 실행 제한 10분.
- 비밀번호는 기존 Secret을 참조하며 파일에는 소유자만 읽기/쓰기 권한 부여.

DB 볼륨과 분리됐지만 같은 서버의 디스크에 저장한다. 서버·디스크 고장에는 외부 사본이 필요하다. local-path의 1Gi 요청은 보통 디스크 사용량의 강제 제한이 아니므로 서버 여유 공간도 확인한다.

## 확인 및 수동 백업

```sh
kubectl -n day-to-us get cronjob postgres-backup
kubectl -n day-to-us get jobs --sort-by=.metadata.creationTimestamp
kubectl -n day-to-us create job --from=cronjob/postgres-backup postgres-backup-manual-$(date +%s)
kubectl -n day-to-us logs job/<생성된-job-이름>
```

성공/실패 Job은 각각 최근 3개를 보관한다. 별도 실패 알림 채널은 아직 연결하지 않았다. 서버가 중단된 경우 1시간 내의 누락된 실행은 복구 후 실행 대상이 되고, 그보다 오래 지난 실행은 다음 정기 일정으로 넘어갈 수 있다.

## 실제 복원 검증

저장소를 읽기 전용으로 연결하고, 임시 emptyDir 안에 별도 PostgreSQL을 실행한다. 운영 DB 접속 정보나 운영 DB 볼륨을 사용하지 않는다.

```sh
kubectl create -f k8s/checks/db-restore-check.yaml
kubectl -n day-to-us wait --for=condition=complete job/<생성된-job-이름> --timeout=120s
kubectl -n day-to-us logs job/<생성된-job-이름>
```

`Restore verified`와 테이블 수가 출력되면 최신 완료 백업의 실제 복원이 성공한 것이다. 검증 Job과 임시 데이터는 종료 1시간 후 자동 정리된다. 이 파일은 정기 배포 Kustomization에 포함하지 않는다.

운영 복구 시에는 앱 쓰기를 중지하고 별도 DB에 먼저 복원·확인한 뒤 연결을 전환한다. `pg_restore --exit-on-error --no-owner --no-privileges`를 사용하며, 기존 운영 DB에 바로 덮어쓰지 않는다. DB 역할/비밀번호와 Kubernetes Secret은 이 백업에 포함되지 않는다. 특히 암호화된 OAuth 토큰을 다시 읽으려면 기존 암호화 키도 별도로 보관해야 한다.

## 저장소 삭제 방지

백업 PVC에 Argo CD의 `Prune=false,Delete=false`를 설정해 자동 정리와 앱 삭제 시 보호한다. 수동 PVC 삭제나 서버 디스크 손상까지 방지하는 설정은 아니다.

참고: [Kubernetes CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/), [PostgreSQL pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html).

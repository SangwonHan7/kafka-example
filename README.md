# 주문-결제 시스템의 Saga 패턴 구현

본 프로젝트는 마이크로서비스 아키텍처에서 분산 트랜잭션을 관리하는 Saga 패턴을 구현한 사례입니다. 주문 생성과 결제 처리 과정에서 오류 발생 시 데이터 일관성을 유지하기 위한 보상 트랜잭션(Compensating Transaction)을 제공합니다.

## 📋 목차
- [아키텍처 개요](#아키텍처-개요)
- [주요 기능](#주요-기능)
- [플로우 다이어그램](#플로우-다이어그램)
- [시퀀스 다이어그램](#시퀀스-다이어그램)
- [상태 다이어그램](#상태-다이어그램)
- [데이터베이스 설계](#데이터베이스-설계)
- [API 사용법](#api-사용법)

## 🏗 아키텍처 개요

본 시스템은 다음과 같은 구성 요소로 이루어져 있습니다:

- **주문 서비스 (Order Service)**: 주문 생성 및 Saga 오케스트레이션 담당
- **결제 서비스 (Payment Service)**: 결제 처리 및 결제 취소 기능 제공
- **Kafka**: 서비스 간 비동기 통신을 위한 메시지 브로커
- **Saga 모니터링 서비스**: 타임아웃된 트랜잭션 관리 및 통계 수집

## 🚀 주요 기능

1. **Saga 오케스트레이션**: 주문-결제 프로세스의 전체 흐름 관리
2. **보상 트랜잭션**: 결제 실패 시 자동으로 주문 취소 처리
3. **타임아웃 관리**: 처리 지연된 트랜잭션의 자동 정리
4. **상태 모니터링**: Saga 트랜잭션의 실시간 상태 추적
5. **자동 재시도**: 실패한 트랜잭션의 수동/자동 재시도 기능

## 🔄 플로우 다이어그램

주문-결제 처리의 전체 흐름을 보여주는 다이어그램입니다.

![플로우 다이어그램](saga-flow-diagram.md)

## 📊 시퀀스 다이어그램

서비스 간 통신과 시간 순서에 따른 메시지 흐름을 보여주는 다이어그램입니다.

![시퀀스 다이어그램](saga-sequence-diagram.md)

## 🔄 상태 다이어그램

Saga 트랜잭션의 상태 전이를 보여주는 다이어그램입니다.

![상태 다이어그램](saga-state-diagram.md)

## 💾 데이터베이스 설계

시스템에서 사용하는 테이블 구조와 관계를 보여주는 ERD 다이어그램입니다.

![ERD 다이어그램](saga-erd-diagram.md)

## 📝 API 사용법

### 주문 생성 (보상 트랜잭션 포함)

```bash
POST /api/saga/orders
Content-Type: application/json

{
  "orderId": "ORDER-001",
  "amount": 50000,
  "currency": "KRW",
  "paymentMethod": "CARD"
}
```

### Saga 상태 확인

```bash
GET /api/saga/orders/{orderId}/status
```

### 진행 중인 모든 Saga 조회

```bash
GET /api/saga/transactions/in-progress
```

### 특정 Saga 재시도

```bash
POST /api/saga/transactions/{sagaId}/retry
``` 
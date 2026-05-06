# 🚦 Rate Limiting Algorithms (Fixed Window, Sliding Window, Token Bucket)

## 📌 Overview

Rate limiting controls how many requests a client (IP/user) can send within a certain time.
It protects systems from abuse, overload, and ensures fair usage.

---

# 🧠 1. Fixed Window

## ✅ Idea

Divide time into fixed intervals (e.g., 60 seconds) and count requests inside each interval.

---

## ⚙️ How it works

* Each request increments a counter
* Counter resets when a new time window starts

---

## 📊 Example

Limit = 10 requests / 60 seconds

```
00:00–00:59 → user sends 10 requests ✔ allowed
01:00–01:59 → counter resets → 10 more ✔ allowed
```

---

## ❌ Problem (Boundary Burst)

User can exploit window edges:

```
00:59 → 10 requests
01:00 → 10 requests
→ 20 requests in ~1 second ❌
```

---

## ✔ Pros

* Very simple
* Fast (O(1))
* Low memory

---

## ❌ Cons

* Unfair
* Burst at window boundaries

---

# 🧠 2. Sliding Window

## ✅ Idea

Instead of fixed intervals, track requests in the **last X seconds continuously**.

---

## ⚙️ How it works

* Store timestamp of each request
* Remove old timestamps (outside window)
* Count remaining requests

---

## 📊 Example

Limit = 10 requests / 60 seconds

```
Now = 12:00:30
Keep requests from 11:59:30 → 12:00:30
```

---

## ✔ Pros

* Accurate
* No boundary burst
* Fair

---

## ❌ Cons

* High memory (stores all timestamps)
* More CPU work (remove + count)

---

## 💾 Data structure

* Redis ZSET (Sorted Set)
* Score = timestamp

---

# 🧠 3. Token Bucket (Best Practical Solution)

## ✅ Idea

Requests consume tokens. Tokens refill over time.

---

## ⚙️ How it works

* Bucket has limited capacity
* Each request uses 1 token
* Tokens refill gradually (e.g., 1 token/sec)

---

## 📊 Example

Capacity = 10
Refill = 1 token/sec

```
Start → 10 tokens
User sends 10 requests → tokens = 0

Wait 5 seconds → +5 tokens
User can send 5 requests
```

---

## 🧠 Formula

```
elapsed = now - lastRefill
tokens += elapsed × refillRate
tokens = min(capacity, tokens)
```

---

## ✔ Pros

* Allows controlled bursts
* Smooth traffic
* Low memory
* Production-friendly

---

## ❌ Cons

* Slightly more complex
* Needs careful implementation (atomic operations)

---

## 💾 Data stored

Per user/IP:

```
tokens
lastRefill
```

---

# ⚖️ Comparison

| Feature        | Fixed Window | Sliding Window | Token Bucket |
| -------------- | ------------ | -------------- | ------------ |
| Accuracy       | Low          | High           | High         |
| Memory         | Low          | High           | Low          |
| CPU            | Low          | Medium         | Low          |
| Burst Handling | Poor         | Medium         | Excellent    |
| Production Use | Rare         | Sometimes      | Most common  |

---

# 🧠 Key Differences

* **Fixed Window** → counts requests per time block
* **Sliding Window** → counts requests in last X seconds
* **Token Bucket** → controls request flow using tokens

---

# 🚀 When to use what?

| Use Case            | Algorithm      |
| ------------------- | -------------- |
| Simple apps         | Fixed Window   |
| Need fairness       | Sliding Window |
| Real systems / APIs | Token Bucket   |

---

# 💡 Final Insight

All rate limiters follow the same pattern:

```
1. Read state
2. Update state using time/request
3. Decide allow or block
4. Save state
```

---

# 🔥 One-line summary

* Fixed Window → “count per time slot”
* Sliding Window → “count recent requests”
* Token Bucket → “spend and earn tokens”

---

# 🧠 Final Recommendation

> Use **Token Bucket** in production systems for best balance between performance, fairness, and scalability.

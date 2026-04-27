var userId = sharedState.get("username")

  var identity = idRepository.getIdentity(userId)
  var raw = identity.getAttribute("pendingApprovalChallenge")

  if (raw === null || raw.isEmpty()) {
      outcome = "none"
  } else {
      var record = JSON.parse(raw.iterator().next())

      if (Date.now() > record.expiry) {
          outcome = "none"
      } else {
          // surface to mobile via callbacks
          sharedState.put("pendingChallengeId", record.challengeId)
          sharedState.put("pendingDisplayCode", record.displayCode)
          outcome = "found"
      }
  }
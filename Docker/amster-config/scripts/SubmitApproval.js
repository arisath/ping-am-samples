  var userId = sharedState.get("username")
  var challengeId = sharedState.get("pendingChallengeId")
  var signature = sharedState.get("approvalSignature") // sent by mobile SDK
  var decision = sharedState.get("decision") // "APPROVED" or "REJECTED"

  var identity = idRepository.getIdentity(userId)
  var raw = identity.getAttribute("pendingApprovalChallenge")

  if (raw === null || raw.isEmpty()) {
      outcome = "expired"
  } else {
      var record = JSON.parse(raw.iterator().next())

      if (record.challengeId !== challengeId || Date.now() > record.expiry) {
          outcome = "expired"
      } else {
          record.status = decision
          record.signature = signature

          identity.setAttribute("pendingApprovalChallenge", [JSON.stringify(record)])
          idRepository.updateIdentity(identity)
          outcome = "success"
      }
  }
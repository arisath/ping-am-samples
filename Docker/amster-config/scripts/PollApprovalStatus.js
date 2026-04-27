 var userId = sharedState.get("username")
  var challengeId = sharedState.get("challengeId")

  var identity = idRepository.getIdentity(userId)
  var raw = identity.getAttribute("pendingApprovalChallenge")

  if (raw === null || raw.isEmpty()) {
      outcome = "expired"
  } else {
      var record = JSON.parse(raw.iterator().next())

      if (record.challengeId !== challengeId) {
          outcome = "expired"
      } else if (Date.now() > record.expiry) {
          outcome = "expired"
      } else if (record.status === "APPROVED") {
          // store signature in transient state for verification
          transientState.put("approvalSignature", record.signature)
          // clean up user store
          identity.setAttribute("pendingApprovalChallenge", [])
          idRepository.updateIdentity(identity)
          outcome = "approved"
      } else if (record.status === "REJECTED") {
          identity.setAttribute("pendingApprovalChallenge", [])
          idRepository.updateIdentity(identity)
          outcome = "rejected"
      } else {
          outcome = "pending" // loop back via a Wait node
      }
  }
  var uuid = java.util.UUID.randomUUID().toString()
  var code = Math.random().toString(36).substring(2, 6).toUpperCase() // e.g. "AB48"
  var userId = sharedState.get("username")

  var challenge = JSON.stringify({
      challengeId: uuid,
      displayCode: code,
      status: "PENDING",
      expiry: Date.now() + 300000,
      signature: null
  })


  // Write to user store
  var identity = idRepository.getIdentity(userId)
  identity.setAttribute("pendingApprovalChallenge", [challenge])
  idRepository.updateIdentity(identity)

  sharedState.put("challengeId", uuid)
  sharedState.put("displayCode", code)

  outcome = "pending"
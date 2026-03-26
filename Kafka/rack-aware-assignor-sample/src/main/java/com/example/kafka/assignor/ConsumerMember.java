package com.example.kafka.assignor;

import java.util.Set;

public record ConsumerMember(String memberId, String rack, Set<String> topics) {
}

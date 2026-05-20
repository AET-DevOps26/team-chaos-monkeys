package com.foundflow.matching.repository;

import java.time.LocalDate;

public interface BucketCountView {

    LocalDate getBucketStart();

    long getCount();
}

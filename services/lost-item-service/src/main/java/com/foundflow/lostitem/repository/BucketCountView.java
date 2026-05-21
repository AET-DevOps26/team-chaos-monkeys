package com.foundflow.lostitem.repository;

import java.time.LocalDate;

public interface BucketCountView {

    LocalDate getBucketStart();

    long getCount();
}

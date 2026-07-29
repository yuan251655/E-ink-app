package com.einkphoto.app.core.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface JobTracker {
    val jobs: StateFlow<Map<DeviceJobId, DeviceJob>>
    fun job(jobId: DeviceJobId): DeviceJob?
}

class InMemoryJobTracker : JobTracker {
    private val mutableJobs = MutableStateFlow<Map<DeviceJobId, DeviceJob>>(emptyMap())
    override val jobs: StateFlow<Map<DeviceJobId, DeviceJob>> = mutableJobs.asStateFlow()

    override fun job(jobId: DeviceJobId): DeviceJob? = mutableJobs.value[jobId]

    fun record(job: DeviceJob) {
        mutableJobs.value = mutableJobs.value + (job.id to job)
    }
}

package com.example.demo.application.task;

import com.example.demo.api.task.TaskRequest;
import com.example.demo.api.task.TaskResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskService {

    private final List<TaskResponse> tasks = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public List<TaskResponse> findAll() {
        return tasks;
    }

    public TaskResponse create(TaskRequest request) {
        TaskResponse task = new TaskResponse(
                idSequence.getAndIncrement(),
                request.title(),
                false
        );

        tasks.add(task);

        return task;
    }

    public TaskResponse markAsDone(Long id) {
        TaskResponse existingTask = tasks.stream()
                .filter(task -> task.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task with id " + id + " was not found"));

        TaskResponse updatedTask = new TaskResponse(
                existingTask.id(),
                existingTask.title(),
                true
        );

        tasks.remove(existingTask);
        tasks.add(updatedTask);

        return updatedTask;
    }
}
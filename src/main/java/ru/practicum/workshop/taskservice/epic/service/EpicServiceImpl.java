package ru.practicum.workshop.taskservice.epic.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.workshop.taskservice.epic.dto.EpicDto;
import ru.practicum.workshop.taskservice.epic.dto.NewEpicDto;
import ru.practicum.workshop.taskservice.epic.dto.UpdateEpicDto;
import ru.practicum.workshop.taskservice.epic.mappers.EpicMapper;
import ru.practicum.workshop.taskservice.epic.model.Epic;
import ru.practicum.workshop.taskservice.epic.repositories.EpicRepository;
import ru.practicum.workshop.taskservice.exceptions.ConflictException;
import ru.practicum.workshop.taskservice.exceptions.ForbiddenException;
import ru.practicum.workshop.taskservice.tasks.model.Task;
import ru.practicum.workshop.taskservice.tasks.repositories.TaskRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.practicum.workshop.taskservice.util.ErrorMessageConstants.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EpicServiceImpl implements EpicService {
    private final EpicMapper epicMapper;
    private final EpicRepository epicRepository;
    private final TaskRepository taskRepository;

    private Epic findEpicInDb(long epicId) {
        return epicRepository.findById(epicId).orElseThrow(
                ()-> new EntityNotFoundException(getNotFoundEpic(epicId)));
    }

    @Override
    public EpicDto createEpic(NewEpicDto dto) {
        Epic newEpic = epicRepository.save(epicMapper.toEntity(dto));
        return epicMapper.toDto(newEpic);
    }

    @Override
    public EpicDto updateEpic(long epicId, long ownerId, UpdateEpicDto dto) {
        Epic updatingEpic = findEpicInDb(epicId);
        if (updatingEpic.getOwnerId() == ownerId) {
            epicMapper.updateEpic(updatingEpic, dto);
            return epicMapper.toDto(epicRepository.save(updatingEpic));
        } else throw new ForbiddenException(FORBIDDEN_UPDATE_EPIC_MESSAGE);
    }

    private void checkExistenceTasks(List<Task> tasks, Set<Long> taskIds) {
        if (tasks.size() < taskIds.size()) {
            List<Long> gotTasks = tasks.stream().map(Task::getId).toList();
            List<Long> notFoundTasks = taskIds.stream()
                    .filter((taskId) -> !gotTasks.contains(taskId))
                    .toList();
            throw new EntityNotFoundException(getNotFoundAddingTasks(notFoundTasks));
        }
    }

    private void checkTasksEventId(long eventId, List<Task> tasks) {
        List<Long> otherEventTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getEventId() != eventId) {
                otherEventTasks.add(task.getId());
            }
        }
        if (!otherEventTasks.isEmpty()) {
            throw new ConflictException(getConflictAddTasks(otherEventTasks, eventId));
        }
    }

    @Override
    public EpicDto addTasks(long epicId, long ownerId, Set<Long> taskIds) {
        List<Task> tasks = taskRepository.findAllById(taskIds);

        checkExistenceTasks(tasks, taskIds);

        Epic epic = findEpicInDb(epicId);

        checkTasksEventId(epic.getEventId(), tasks);

        if (epic.getOwnerId() != ownerId) throw new ForbiddenException(FORBIDDEN_ADD_TASK_EPIC_MESSAGE);

        if (epic.getTasks() != null) {
            epic.getTasks().addAll(tasks);
        } else epic.setTasks(new HashSet<>(tasks));

        return epicMapper.toDto(epicRepository.save(epic));
    }

    @Transactional(readOnly = true)
    @Override
    public EpicDto getEpicById(long id) {
        return epicMapper.toDto(findEpicInDb(id));
    }

    @Override
    public void deleteEpicById(long epicId, long ownerId) {
        Epic epic = findEpicInDb(epicId);
        if (epic.getOwnerId() == ownerId) {
            epicRepository.delete(epic);
        } else throw new ForbiddenException(FORBIDDEN_DELETE_EPIC_MESSAGE);
    }
}
package com.mm.greenstep.domain.plogging.service;

import com.mm.greenstep.domain.avatar.entity.Avatar;
import com.mm.greenstep.domain.avatar.entity.UserAvatar;
import com.mm.greenstep.domain.avatar.repository.AvatarRepository;
import com.mm.greenstep.domain.avatar.repository.UserAvatarRepository;
import com.mm.greenstep.domain.plogging.dto.request.PloggingCoorDto;
import com.mm.greenstep.domain.plogging.dto.request.PloggingReqDto;
import com.mm.greenstep.domain.plogging.dto.response.PloggingAllResDto;
import com.mm.greenstep.domain.plogging.dto.response.PloggingDetailResDto;
import com.mm.greenstep.domain.plogging.dto.response.PloggingResDto;
import com.mm.greenstep.domain.plogging.entity.Plogging;
import com.mm.greenstep.domain.plogging.entity.Position;
import com.mm.greenstep.domain.plogging.repository.PloggingRepository;
import com.mm.greenstep.domain.plogging.repository.PositionRepository;
import com.mm.greenstep.domain.plogging.repository.TrashRepository;
import com.mm.greenstep.domain.user.entity.User;
import com.mm.greenstep.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PloggingService {

    private final UserRepository userRepository;
    private final PloggingRepository ploggingRepository;
    private final AvatarRepository avatarRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final AmazonS3Service amazonS3Service;
    private final PositionRepository positionRepository;
    private final TrashRepository trashRepository;

    public PloggingResDto createPlogging(HttpServletRequest request, PloggingReqDto dto) {
        Boolean levelUp = false;
        String avatarImg = "";
        String avatarName = "";

        Long user_pk = 100L;
        User user = userRepository.findByUserId(user_pk);

        // 종료시간에서 - 전체 이동시간(Double TravelTime) 빼서 시작시간 만들기
        LocalDateTime endTime = LocalDateTime.now();
        // travelTime은 분 단위입니다. 이를 초 단위로 변환합니다.
        long travelTimeInSeconds = (long) (dto.getTravelTime() * 60);
        LocalDateTime startTime = endTime.minusSeconds(travelTimeInSeconds);

        // 경험치 계산
        Integer getExp = null;
//                dto.getAITrashAmount() +
//                        dto.getTravelRange() +
//                        dto.getTravelTime() +
//                        dto.getTrashAmount();

        Plogging plogging = Plogging.builder()
                .createdAt(startTime)
                .updatedAt(endTime)
                .user(user)
                .travelTime(dto.getTravelTime())
                .travelRange(dto.getTravelRange())
                .getExp(getExp)
                .build();

        // 쓰레기 량
        Integer trashAmount = dto.getTrashAmount() + dto.getAITrashAmount();

        // 경험치 계산
        Integer exp = user.getExp() + getExp;

        // 레벨업 계산
        if (exp >= 100) {
            Integer curExp = exp - 100;
            user.levelUp(curExp);
            levelUp = true;

            // 랜덤 아바타 선택을 위한 쿼리
            Avatar randomAvatar = avatarRepository.findRandomAvatar();
            // 선택된 랜덤 아바타를 `user_avatar` 테이블에 추가
            UserAvatar userAvatar = userAvatarRepository.findByUser(user);
            // 유저의 아바타 새로 뽑은거로 업데이트해주고
            userAvatar.updateAvatar(randomAvatar);
            // 저장
            userAvatarRepository.save(userAvatar);
            // dto에 넣어서 보내주기 위한 아바타 사진 주소와 아바타 이름
            avatarImg = randomAvatar.getAvatarImg();
            avatarName = randomAvatar.getAvatarName();
        }

        userRepository.save(user);
        ploggingRepository.save(plogging);

        PloggingResDto responseDto = PloggingResDto.builder()
                .trashAmount(getExp)
                .travelRange(dto.getTravelRange())
                .trashAmount(trashAmount)
                .travelTime(dto.getTravelTime())
                .isLevelUp(levelUp)
                .avatarImg(avatarImg)
                .avatarName(avatarName)
                .getExp(getExp)
                .build();

        // 플로깅 모든 위도 경도 등록
        for (PloggingCoorDto c : dto.getCoorList()) {
            Position p = Position.builder()
                    .latitude(c.getLatitude())
                    .longitude(c.getLongitude())
                    .plogging(plogging)
                    .build();
            positionRepository.save(p);
        }

        // 플로깅 모든 쓰레기 좌표 등록 // 이부분은 쓰레기등록 api로 뺴야할듯
//        for (PloggingTrashReqDto tr : dto.getTrashList()) {
//            Trash t = Trash.builder()
//                    .plogging(plogging)
//                    .
//
//                    .build();
//            positionRepository.save(p);
//        }

        return responseDto;
    }

    public void updatePloggingImg(MultipartFile file, Long ploggingId) {
        Plogging plogging = ploggingRepository.findByPloggingId(ploggingId);

        String s3Url = amazonS3Service.uploadFile(file);

        plogging.updatePloggingImg(s3Url);
        ploggingRepository.save(plogging);
    }

    public List<PloggingAllResDto> getAllPlogging(HttpServletRequest request) {
        List<PloggingAllResDto> dtoList = new ArrayList<>();
        Long user_pk = 100L;
        User user = userRepository.findByUserId(user_pk);
        List<Plogging> plogging = ploggingRepository.findAllByUser(user);

        for (Plogging p : plogging) {
            PloggingAllResDto dto = PloggingAllResDto.builder()
                    .ploggingId(p.getPloggingId())
                    .createdAt(p.getCreatedAt())
                    .getExp(p.getGetExp())
                    .travelRange(p.getTravelRange())
                    .trashAmount(p.getTrashAmount())
                    .travelTime(p.getTravelTime())
                    .build();
            dtoList.add(dto);
        }


        return dtoList;
    }

    public PloggingDetailResDto getDetailPlogging(Long ploggingId) {
        Plogging plogging = ploggingRepository.findByPloggingId(ploggingId);
        List<Position> position = positionRepository.findAllByPlogging(plogging);
        List<PloggingCoorDto> ploggingCoorDtoList = new ArrayList<>();
        for (Position p : position) {
            PloggingCoorDto dto = PloggingCoorDto.builder()
                    .latitude(p.getLatitude())
                    .longitude(p.getLongitude())
                    .build();
            ploggingCoorDtoList.add(dto);
        }

        PloggingDetailResDto dto = PloggingDetailResDto.builder()
                .createdAt(plogging.getCreatedAt())
                .getExp(plogging.getGetExp())
                .travelRange(plogging.getTravelRange())
                .trashAmount(plogging.getTrashAmount())
                .travelTime(plogging.getTravelTime())
                .coorList(ploggingCoorDtoList)
                .travelPicture(plogging.getTravelPicture())
                .build();

        return dto;
    }
}
